package co.turismo.usecase.place.filterStrategy;

import co.turismo.model.place.Place;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.gateways.PlaceRepository;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.model.place.strategy.PlaceSearchStrategy;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

public class TextSearchStrategy implements PlaceSearchStrategy {
    private final PlaceRepository placeRepository;

    public TextSearchStrategy(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    public PlaceSearchMode mode() {
        return PlaceSearchMode.TEXT;
    }


    @Override
    public Flux<Place> execute(PlaceSearchCriteria c) {
        return placeRepository.search(
                c.getQ(),
                c.getCategoryId(),
                c.isOnlyNearby(),
                c.getLat(),
                c.getLng(),
                c.getRadiusMeters(),
                c.getPage(),
                c.getSize());
    }
}
