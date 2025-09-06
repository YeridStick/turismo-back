package co.turismo.model.place.gateways;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceRepository {
    Mono<Place> create(CreatePlaceRequest request);
    Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit);
    Mono<Place> verifyPlace(long id, boolean verified, boolean active, long adminId);
    Mono<Place> setActive(long id, boolean active);

    // NUEVOS (reglas de pertenencia resueltas en el adapter)
    Mono<Place> setActiveIfOwner(String ownerEmail, long placeId, boolean active);
    Mono<Void>  addOwnerToPlace(String ownerEmailToAdd, long placeId);
    Mono<Void>  removeOwnerFromPlace(String ownerEmailToRemove, long placeId);
    Flux<Place> findPlacesByOwnerEmail(String ownerEmail);
}