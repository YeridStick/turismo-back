package co.turismo.model.place.gateways;

import co.turismo.model.place.Place;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceRepository {
    Mono<Place> create(Place place, double lat, double lng);
    Flux<Place> findNearby(double lat, double lng, double radiusMeters, int limit);
    Mono<Place> verifyPlace(long id, boolean verified, boolean active, long adminId);
    Mono<Place> setActive(long id, boolean active);
}