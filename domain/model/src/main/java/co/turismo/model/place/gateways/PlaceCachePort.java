package co.turismo.model.place.gateways;

import co.turismo.model.place.Place;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PlaceCachePort {

    Mono<List<Place>> getSearchResults(PlaceSearchCriteria criteria);

    Mono<Boolean> saveSearchResults(PlaceSearchCriteria criteria, List<Place> places);

    Mono<Void> invalidateSearchCache();

    Mono<Void> invalidatePlaceDetail(Long placeId);
}
