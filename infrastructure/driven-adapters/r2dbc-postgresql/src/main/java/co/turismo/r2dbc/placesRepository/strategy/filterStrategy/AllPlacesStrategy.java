package co.turismo.r2dbc.placesRepository.strategy.filterStrategy;

import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.model.place.strategy.PlaceSearchStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class AllPlacesStrategy implements PlaceSearchStrategy {

    private final PlaceRepository placeRepository;

    @Override
    public PlaceSearchMode mode() {
        return PlaceSearchMode.ALL;
    }

    @Override
    public Flux<Place> execute(PlaceSearchCriteria c) {
        int limit = (c.getSize() > 0) ? c.getSize() : 10;
        int offset = (c.getPage() > 0) ? (c.getPage() * limit) : 0;
        return placeRepository.findAllPlace(limit, offset);
    }
}
