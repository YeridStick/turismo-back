package co.turismo.model.place.gateways;

import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.Place;
import co.turismo.model.place.UpdatePlaceRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceRepository {
    Mono<Place> create(CreatePlaceRequest request);
    Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit, Long categoryId);
    Flux<Place> findAllPlace();
    Flux<Place> search(String q, Long categoryId, boolean onlyNearby, Double lat, Double lng, Double radiusMeters, int page, int size);
    Mono<Place> patch(long id, UpdatePlaceRequest req);
    Mono<Place> verifyPlace(long id, boolean verified, boolean active, long adminId);
    Mono<Place> setActive(long id, boolean active);

    Mono<Place> setActiveIfOwner(String ownerEmail, long placeId, boolean active);
    Mono<Void>  addOwnerToPlace(String ownerEmailToAdd, long placeId);
    Mono<Void>  removeOwnerFromPlace(String ownerEmailToRemove, long placeId);
    Flux<Place> findPlacesByOwnerEmail(String ownerEmail);
    Mono<Place> findByPlaces(Long id);
}