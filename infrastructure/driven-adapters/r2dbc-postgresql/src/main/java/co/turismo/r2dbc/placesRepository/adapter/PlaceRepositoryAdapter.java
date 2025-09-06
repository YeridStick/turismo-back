package co.turismo.r2dbc.placesRepository.adapter;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.user.User; // asume que existe
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.r2dbc.placesRepository.entity.PlaceData;
import co.turismo.r2dbc.placesRepository.entity.PlaceOwnerData;
import co.turismo.r2dbc.placesRepository.repository.PlaceAdapterRepository;
import co.turismo.r2dbc.placesRepository.repository.PlaceOwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryAdapter implements PlaceRepository {

    private final PlaceAdapterRepository repo;
    private final PlaceOwnerRepository repoOwner;
    private final UserRepository userRepository;

    @Override
    public Mono<Place> create(CreatePlaceRequest request) {
        return userRepository.findByEmail(request.getOwnerEmail())
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado")))
                .flatMap(user ->
                        repo.insertPlace(
                                        user.getId(),                          // <<--- AQUÍ
                                        request.getName(), request.getDescription(), request.getCategory(),
                                        request.getLat(), request.getLng(), request.getAddress(),
                                        request.getPhone(), request.getWebsite()
                                )
                                .flatMap(placeData ->
                                        saveRelation(placeData.getId(), user.getId())
                                                .thenReturn(toDomain(placeData))
                                )
                );
    }

    private Mono<Void> saveRelation(Long placeId, Long userId) {
        var relation = PlaceOwnerData.builder()
                .placeId(placeId)
                .userId(userId)
                .build();
        return repoOwner.save(relation)
                .onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
                .then();
    }

    @Override
    public Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit) {
        return repo.findNearby(lat, lng, radiusMeters, limit).map(this::toDomain);
    }

    @Override
    public Mono<Place> verifyPlace(long id, boolean verified, boolean active, long adminId) {
        return repo.verifyPlace(id, verified, active, adminId).map(this::toDomain);
    }

    @Override
    public Mono<Place> setActive(long id, boolean active) {
        return repo.setActive(id, active).map(this::toDomain);
    }

    // ====== NUEVOS ======

    @Override
    public Mono<Place> setActiveIfOwner(String ownerEmail, long placeId, boolean active) {
        return userRepository.findByEmail(ownerEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado")))
                .flatMap(owner ->
                        repoOwner.existsByPlaceIdAndUserId(placeId, owner.getId())
                                .flatMap(exists -> {
                                    if (!exists) return Mono.error(new RuntimeException("No autorizado para este lugar"));
                                    return repo.setActive(placeId, active);
                                })
                )
                .map(this::toDomain);
    }

    @Override
    public Mono<Void> addOwnerToPlace(String ownerEmailToAdd, long placeId) {
        return resolveUser(ownerEmailToAdd)
                .flatMap(user ->
                        repoOwner.existsByPlaceIdAndUserId(placeId, user.getId())
                                .flatMap(exists -> exists
                                        ? Mono.empty()
                                        : repoOwner.save(PlaceOwnerData.builder()
                                                .placeId(placeId)
                                                .userId(user.getId())
                                                .build())
                                        .then()
                                )
                )
                .onErrorResume(DuplicateKeyException.class, e -> Mono.empty())
                .then();
    }

    @Override
    public Mono<Void> removeOwnerFromPlace(String ownerEmailToRemove, long placeId) {
        return resolveUser(ownerEmailToRemove)
                .flatMap(user -> repoOwner.deleteByPlaceIdAndUserId(placeId, user.getId()));
    }

    @Override
    public Flux<Place> findPlacesByOwnerEmail(String ownerEmail) {
        return repo.findMine(ownerEmail).map(this::toDomain);
    }

    private Mono<User> resolveUser(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario no encontrado: " + email)));
    }

    private Place toDomain(PlaceData r) {
        return Place.builder()
                .id(r.getId())
                .name(r.getName())
                .description(r.getDescription())
                .category(r.getCategory())
                .lat(r.getLat())
                .lng(r.getLng())
                .address(r.getAddress())
                .phone(r.getPhone())
                .website(r.getWebsite())
                .verified(r.getIsVerified())
                .active(r.getIsActive())
                .createdAt(r.getCreatedAt())
                .build();
    }

    public Flux<Place> findMine(String ownerEmail) {
        return resolveUser(ownerEmail)
                .flatMapMany(user -> repo.findByOwnerId(user.getId()))
                .map(this::toDomain);
    }

}
