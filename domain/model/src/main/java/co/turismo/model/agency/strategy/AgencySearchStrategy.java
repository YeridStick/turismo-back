package co.turismo.model.agency.strategy;

import co.turismo.model.agency.Agency;
import reactor.core.publisher.Flux;

public interface AgencySearchStrategy {
    AgencySearchMode mode();
    Flux<Agency> execute(AgencySearchCriteria criteria);
}
