package co.turismo.r2dbc.placesRepository.strategy.filterFactory;

import co.turismo.model.place.strategy.PlaceSearchFactoryGateway;
import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.model.place.strategy.PlaceSearchStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PlaceSearchFactory implements PlaceSearchFactoryGateway {
    private final Map<PlaceSearchMode, PlaceSearchStrategy> strategies;

    public PlaceSearchFactory(List<PlaceSearchStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(PlaceSearchStrategy::mode, s -> s));
    }

    @Override
    public PlaceSearchStrategy getStrategy(PlaceSearchMode mode) {
        PlaceSearchStrategy s = strategies.get(mode);
        if (s == null) throw new IllegalArgumentException("Modo no soportado: " + mode);
        return s;
    }
}
