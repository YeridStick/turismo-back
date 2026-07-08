package co.turismo.api.dto.tourpackage;

import co.turismo.model.place.Place;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "TourPackageResponse")
public record TourPackageResponse(
        String id,
        String title,
        String city,
        String description,
        Integer days,
        Integer nights,
        String people,
        Double rating,
        Long reviews,
        Long price,
        Long originalPrice,
        String discount,
        String tag,
        List<String> includes,
        String image,
        List<Long> placeIds,
        Long agencyId,
        String agencyName,
        List<Place> places
) {}
