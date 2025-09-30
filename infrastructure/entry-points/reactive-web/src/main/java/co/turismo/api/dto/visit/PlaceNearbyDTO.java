package co.turismo.api.dto.visit;

public record PlaceNearbyDTO(
        PlaceBrief place,
        Integer distanceM
) {}