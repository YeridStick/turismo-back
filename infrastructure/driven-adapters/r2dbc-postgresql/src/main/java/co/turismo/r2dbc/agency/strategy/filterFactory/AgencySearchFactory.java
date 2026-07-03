package co.turismo.r2dbc.agency.strategy.filterFactory;

import co.turismo.model.agency.strategy.AgencySearchFactoryGateway;
import co.turismo.model.agency.strategy.AgencySearchMode;
import co.turismo.model.agency.strategy.AgencySearchStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AgencySearchFactory implements AgencySearchFactoryGateway {
    private final Map<AgencySearchMode, AgencySearchStrategy> strategies;

    public AgencySearchFactory(List<AgencySearchStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(AgencySearchStrategy::mode, s -> s));
    }

    @Override
    public AgencySearchStrategy getStrategy(AgencySearchMode mode) {
        AgencySearchStrategy strategy = strategies.get(mode);
        if (strategy == null) throw new IllegalArgumentException("Modo de busqueda no soportado: " + mode);
        return strategy;
    }
}
