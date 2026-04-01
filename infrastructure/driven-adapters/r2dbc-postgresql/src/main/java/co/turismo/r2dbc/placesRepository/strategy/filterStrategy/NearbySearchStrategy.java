package co.turismo.r2dbc.placesRepository.strategy.filterStrategy;

import co.turismo.model.place.Place;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.model.place.strategy.PlaceSearchStrategy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
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
        if (c.getLat() == null || c.getLng() == null) {
            return Flux.error(new IllegalArgumentException("Para el modo NEARBY se requieren latitud (lat) y longitud (lng)"));
        }
        double radius = (c.getRadiusMeters() != null) ? c.getRadiusMeters() : 5000.0;
        int limit = (c.getSize() > 0) ? c.getSize() : 10;
        int offset = (c.getPage() > 0) ? (c.getPage() * limit) : 0;
        return placeRepository.findNearby(c.getLat(), c.getLng(), radius, c.getCategoryId(), limit, offset);
    }

}
