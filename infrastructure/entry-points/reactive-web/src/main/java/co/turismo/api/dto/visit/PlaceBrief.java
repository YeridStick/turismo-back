package co.turismo.api.dto.visit;

import java.util.List;

public record PlaceBrief(
        Long id,
        String name,
        Double lat,
        Double lng,
        String address,
        String description,
        Integer categoryId,
        List<String> imageUrls
) {}

