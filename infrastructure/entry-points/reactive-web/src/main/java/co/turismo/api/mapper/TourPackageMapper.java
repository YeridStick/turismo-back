package co.turismo.api.mapper;

import co.turismo.api.dto.tourpackage.CreatePackageRequest;
import co.turismo.api.dto.tourpackage.TourPackageResponse;
import co.turismo.api.dto.tourpackage.UpdatePackageBody;
import co.turismo.model.tourpackage.CreateTourPackageRequest;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.UpdateTourPackageRequest;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class TourPackageMapper {

    private TourPackageMapper() {}

    public static CreateTourPackageRequest toCreateCommand(CreatePackageRequest body) {
        return CreateTourPackageRequest.builder()
                .title(body.title())
                .city(body.city())
                .description(body.description())
                .days(body.days())
                .nights(body.nights())
                .people(body.people())
                .rating(body.rating())
                .reviews(body.reviews())
                .price(body.price())
                .originalPrice(body.originalPrice())
                .discount(body.discount())
                .tag(body.tag())
                .includes(body.includes() == null ? new String[0] : body.includes().toArray(String[]::new))
                .image(body.image())
                .placeIds(body.placeIds().toArray(Long[]::new))
                .build();
    }

    public static UpdateTourPackageRequest toUpdateCommand(UpdatePackageBody body) {
        return UpdateTourPackageRequest.builder()
                .title(body.title())
                .city(body.city())
                .description(body.description())
                .days(body.days())
                .nights(body.nights())
                .people(body.people())
                .rating(body.rating())
                .reviews(body.reviews())
                .price(body.price())
                .originalPrice(body.originalPrice())
                .discount(body.discount())
                .tag(body.tag())
                .includes(body.includes() == null ? null : body.includes().toArray(String[]::new))
                .image(body.image())
                .placeIds(body.placeIds() == null ? null : body.placeIds().toArray(Long[]::new))
                .build();
    }

    public static TourPackageResponse toResponse(TourPackage pkg) {
        return new TourPackageResponse(
                pkg.getId() == null ? null : String.valueOf(pkg.getId()),
                pkg.getTitle(),
                pkg.getCity(),
                pkg.getDescription(),
                pkg.getDays(),
                pkg.getNights(),
                pkg.getPeople(),
                pkg.getRating(),
                pkg.getReviews(),
                pkg.getPrice(),
                pkg.getOriginalPrice(),
                pkg.getDiscount(),
                pkg.getTag(),
                pkg.getIncludes() == null ? List.of() : Arrays.asList(pkg.getIncludes()),
                pkg.getImage(),
                List.of(normalizePlaceIds(pkg.getPlaceIds())),
                pkg.getAgencyId(),
                pkg.getAgencyName(),
                pkg.getPlaces() == null ? List.of() : pkg.getPlaces()
        );
    }

    private static Long[] normalizePlaceIds(Long[] placeIds) {
        if (placeIds == null || placeIds.length == 0) {
            return new Long[0];
        }

        var set = new LinkedHashSet<Long>();
        for (Long id : placeIds) {
            if (id != null && id > 0) {
                set.add(id);
            }
        }
        return set.stream().filter(Objects::nonNull).toArray(Long[]::new);
    }
}
