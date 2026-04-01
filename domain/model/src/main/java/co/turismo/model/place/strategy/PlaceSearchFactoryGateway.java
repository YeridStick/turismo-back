package co.turismo.model.place.strategy;

public interface PlaceSearchFactoryGateway {
    PlaceSearchStrategy getStrategy(PlaceSearchMode mode);
}
