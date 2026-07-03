package co.turismo.model.agency.strategy;

public interface AgencySearchFactoryGateway {
    AgencySearchStrategy getStrategy(AgencySearchMode mode);
}
