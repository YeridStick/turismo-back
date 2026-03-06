package co.turismo.model.place.strategy;

import co.turismo.model.place.Place;
import reactor.core.publisher.Flux;

public interface PlaceSearchStrategy {
    PlaceSearchMode mode();
    Flux<Place> execute(PlaceSearchCriteria c);
}
