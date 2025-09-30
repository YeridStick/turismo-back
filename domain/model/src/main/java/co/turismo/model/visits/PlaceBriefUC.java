package co.turismo.model.visits;

import java.util.List;

public record PlaceBriefUC(
        Long id, String name, String address, String description,
        Integer categoryId, Double lat, Double lng, List<String> imageUrls
) {}