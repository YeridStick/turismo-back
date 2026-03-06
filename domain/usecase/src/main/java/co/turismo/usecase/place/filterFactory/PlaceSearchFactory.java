package co.turismo.usecase.place.filterFactory;

import co.turismo.model.place.strategy.PlaceSearchMode;
import co.turismo.model.place.strategy.PlaceSearchStrategy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PlaceSearchFactory {
    private final Map<PlaceSearchMode, PlaceSearchStrategy> strategies;

    public PlaceSearchFactory(List<PlaceSearchStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(PlaceSearchStrategy::mode, s -> s));
    }

    public PlaceSearchStrategy getStrategy(PlaceSearchMode mode) {
        PlaceSearchStrategy s = strategies.get(mode);
        if (s == null) throw new IllegalArgumentException("Modo no soportado: " + mode);
        return s;
    }
}
