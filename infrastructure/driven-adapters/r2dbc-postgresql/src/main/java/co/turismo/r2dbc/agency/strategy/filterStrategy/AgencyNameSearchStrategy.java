package co.turismo.r2dbc.agency.strategy.filterStrategy;

import co.turismo.model.agency.Agency;
import co.turismo.model.agency.gateways.AgencyRepository;
import co.turismo.model.agency.strategy.AgencySearchCriteria;
import co.turismo.model.agency.strategy.AgencySearchMode;
import co.turismo.model.agency.strategy.AgencySearchStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class AgencyNameSearchStrategy implements AgencySearchStrategy {

    private final AgencyRepository agencyRepository;

    @Override
    public AgencySearchMode mode() {
        return AgencySearchMode.NAME;
    }

    @Override
    public Flux<Agency> execute(AgencySearchCriteria criteria) {
        return agencyRepository.findByNameLike(criteria.getQ(), criteria.getLimit(), criteria.getOffset());
    }
}
