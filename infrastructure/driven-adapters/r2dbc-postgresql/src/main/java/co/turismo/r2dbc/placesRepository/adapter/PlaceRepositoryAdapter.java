package co.turismo.r2dbc.placesRepository.adapter;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.UpdatePlaceRequest;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.r2dbc.placesRepository.entity.PlaceData;
import co.turismo.r2dbc.placesRepository.repository.PlaceAdapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryAdapter implements PlaceRepository {

    private final PlaceAdapterRepository repo;
    private final UserRepository userRepository;

    @Override
    public Mono<Place> create(CreatePlaceRequest request) {
        return userRepository.findByEmail(request.getOwnerEmail())
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado: " + request.getOwnerEmail())))
                .flatMap(user -> {
                    if (user.getId() == null)
                        return Mono.error(new RuntimeException("Owner encontrado pero con id NULL (revisa mapping de UserRepository)"));
                    return repo.insertPlace(
                            user.getId(),
                            request.getName(),
                            request.getDescription(),
                            request.getCategoryId(),
                            request.getLat(),
                            request.getLng(),
                            request.getAddress(),
                            request.getPhone(),
                            request.getWebsite(),
                            request.getImageUrls() == null ? new String[0] : request.getImageUrls()
                    );
                })
                .map(this::toDomain);
    }

    @Override
    public Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit, Long categoryId) {
        return repo.findNearby(lat, lng, radiusMeters, limit, categoryId).map(this::toDomain);
    }

    @Override
    public Flux<Place> search(String q, Long categoryId, boolean onlyNearby,
                              Double lat, Double lng, Double radiusMeters, int page, int size) {
        int limit  = Math.max(1, size);
        int offset = Math.max(0, page) * limit;
        return repo.search(q, categoryId, onlyNearby, lat, lng, radiusMeters, limit, offset)
                .map(this::toDomain);
    }

    @Override
    public Mono<Place> patch(long id, UpdatePlaceRequest req) {
        return repo.patchPlace(
                        id,
                        req.getName(),
                        req.getDescription(),
                        req.getCategoryId(),
                        req.getLat(),
                        req.getLng(),
                        req.getAddress(),
                        req.getPhone(),
                        req.getWebsite(),
                        req.getImageUrls()
                )
                .switchIfEmpty(Mono.error(new IllegalStateException("Lugar no encontrado")))
                .map(this::toDomain);
    }

    @Override
    public Mono<Place> verifyPlace(long id, boolean verified, boolean active, long adminId) {
        return repo.verifyPlace(id, verified, active, adminId).map(this::toDomain);
    }

    @Override
    public Mono<Place> setActive(long id, boolean active) {
        return repo.setActive(id, active).map(this::toDomain);
    }

    @Override
    public Mono<Place> setActiveIfOwner(String ownerEmail, long placeId, boolean active) {
        return userRepository.findByEmail(ownerEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Owner no encontrado")))
                .flatMap(owner -> repo.setActiveIfOwner(placeId, active, owner.getId()))
                .switchIfEmpty(Mono.error(new RuntimeException("No autorizado para este lugar")))
                .map(this::toDomain);
    }

    @Override
    public Flux<Place> findPlacesByOwnerEmail(String ownerEmail) {
        return userRepository.findByEmail(ownerEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Usuario no encontrado")))
                .flatMapMany(u -> repo.findByOwnerId(u.getId()))
                .map(this::toDomain);
    }

    @Override
    public Mono<Void> addOwnerToPlace(String ownerEmailToAdd, long placeId) {
        return Mono.error(new UnsupportedOperationException("Co‑owners no soportados en la nueva lógica"));
    }

    @Override
    public Mono<Void> removeOwnerFromPlace(String ownerEmailToRemove, long placeId) {
        return Mono.error(new UnsupportedOperationException("Co‑owners no soportados en la nueva lógica"));
    }

    private Place toDomain(PlaceData r) {
        return Place.builder()
                .id(r.getId())
                .ownerUserId(r.getOwnerUserId())
                .name(r.getName())
                .description(r.getDescription())
                .categoryId(r.getCategoryId())
                .lat(r.getLat())
                .lng(r.getLng())
                .address(r.getAddress())
                .phone(r.getPhone())
                .website(r.getWebsite())
                .imageUrls(r.getImageUrls())
                .isVerified(r.getIsVerified())
                .isActive(r.getIsActive())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
