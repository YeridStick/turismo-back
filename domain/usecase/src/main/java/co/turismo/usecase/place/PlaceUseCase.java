package co.turismo.usecase.place;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.UpdatePlaceRequest;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.AccessDeniedException;

@RequiredArgsConstructor
public class PlaceUseCase {

    private final PlaceRepository placeRepository;
    private final UserIdentityPort userIdentityPort;

    public Mono<Place> createPlace(CreatePlaceRequest cmd) {
        return placeRepository.create(cmd);
    }

    public Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit, Long categoryId) {
        return placeRepository.findNearby(lat, lng, radiusMeters, limit, categoryId);
    }

    public Flux<Place> findAllPlaces(){
        return placeRepository.findAllPlace();
    }

    public Mono<Place> verifyPlaceByAdmin(String adminEmail, long placeId, boolean approve) {
        return userIdentityPort.getUserIdForEmail(adminEmail)
                .switchIfEmpty(Mono.error(new RuntimeException("Admin no encontrado")))
                .flatMap(admin -> placeRepository.verifyPlace(placeId, approve, approve, admin.id()));
    }

    public Mono<Place> setActiveByOwner(String ownerEmail, long placeId, boolean active) {
        return placeRepository.setActiveIfOwner(ownerEmail, placeId, active);
    }

    public Flux<Place> findMine(String ownerEmail) {
        return placeRepository.findPlacesByOwnerEmail(ownerEmail);
    }

    public Mono<Place> patch(long id, UpdatePlaceRequest req) {
        return placeRepository.patch(id, req);
    }

    public Flux<Place> search(String q, Long categoryId, boolean onlyNearby,
                              Double lat, Double lng, Double radiusMeters, int page, int size) {
        return placeRepository.search(q, categoryId, onlyNearby, lat, lng, radiusMeters, page, size);
    }

    public Mono<Place> findByIdPlace(long id) {
        return placeRepository.findByPlaces(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Place no encontrado")));
    }

    public Mono<Place> setActive(long placeId, boolean active) {
        return placeRepository.setActive(placeId, active);
    }

    public Mono<Place> deleteById(long id) {
        return placeRepository.deletePalce(id);
    }

    public Mono<Place> deleteByOwnerOrAdmin(String email, long placeId) {
        return userIdentityPort.getUserIdForEmail(email)
            .switchIfEmpty(Mono.error(new RuntimeException("Usuario no encontrado")))
            .flatMap(user ->
                placeRepository.findByPlaces(placeId)
                    .switchIfEmpty(Mono.error(new RuntimeException("Place no encontrado")))
                    .flatMap(place -> {
                        boolean isOwner = place.getOwnerUserId() != null
                                && place.getOwnerUserId().equals(user.id());

                        if (!isOwner) {
                            return Mono.error(new AccessDeniedException("No tienes permiso para eliminar este sitio"));
                        }
                        return placeRepository.deletePalce(placeId);
                    })
            );
    }
}
