package co.turismo.usecase.place.filterStrategy;

import co.turismo.model.place.Place;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.model.place.strategy.PlaceSearchStrategy;
import reactor.core.publisher.Flux;

public class NearbySearchStrategy implements PlaceSearchStrategy {
    private final PlaceRepository placeRepository;

    public NearbySearchStrategy(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    public PlaceSearchMode mode() {
        return PlaceSearchMode.NEARBY;
    }

    @Override
    public Flux<Place> execute(PlaceSearchCriteria c) {
        int limit = (c.getSize() > 0) ? c.getSize() : 10;
        int offset = (c.getPage() > 0) ? (c.getPage() * limit) : 0;
        return placeRepository.findNearby(c.getLat(), c.getLng(), c.getRadiusMeters(), c.getCategoryId(), limit,offset);
    }

}
