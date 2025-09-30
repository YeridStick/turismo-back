package co.turismo.model.visits;

import java.util.List;

public record PlaceNearby(
        Long id, String name,
        Double lat, Double lng,
        String address, String description,
        Integer categoryId, List<String> imageUrls,
        Integer distanceM
) {}