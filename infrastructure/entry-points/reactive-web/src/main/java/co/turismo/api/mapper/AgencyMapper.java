package co.turismo.api.mapper;

import co.turismo.api.dto.agency.*;
import co.turismo.api.dto.visit.TopPlaceDTO;
import co.turismo.model.agency.AgencyDashboard;
import co.turismo.model.agency.CreateAgencyRequest;
import co.turismo.model.agency.UpdateAgencyRequest;
import co.turismo.model.place.Place;
import co.turismo.model.tourpackage.TopPackage;
import co.turismo.model.tourpackage.TourPackage;
import co.turismo.model.tourpackage.TourPackageSalesSummary;

import java.util.Arrays;
import java.util.List;

public final class AgencyMapper {

    private AgencyMapper() {}

    public static CreateAgencyRequest toCreateAgencyRequest(CreateAgencyBody body) {
        return CreateAgencyRequest.builder()
                .name(body.name())
                .description(body.description())
                .phone(body.phone())
                .email(body.email())
                .website(body.website())
                .logoUrl(body.logoUrl())
                .build();
    }

    public static UpdateAgencyRequest toUpdateAgencyRequest(UpdateAgencyBody body) {
        return UpdateAgencyRequest.builder()
                .name(body.name())
                .description(body.description())
                .phone(body.phone())
                .email(body.email())
                .website(body.website())
                .logoUrl(body.logoUrl())
                .build();
    }

    public static AgencyDashboardResponse toDashboardResponse(AgencyDashboard dashboard) {
        List<AgencyTourPackageResponse> packages = dashboard.getPackages() == null
                ? List.of()
                : dashboard.getPackages().stream()
                .map(AgencyMapper::toPackageResponse)
                .toList();

        List<TopPackageResponse> topPackages = dashboard.getTopPackages() == null
                ? List.of()
                : dashboard.getTopPackages().stream()
                .map(AgencyMapper::toTopPackageResponse)
                .toList();

        List<TopPlaceDTO> topPlaces = dashboard.getTopPlaces() == null
                ? List.of()
                : dashboard.getTopPlaces().stream()
                .map(place -> new TopPlaceDTO(
                        place.getPlaceId(),
                        place.getName(),
                        place.getVisits()
                ))
                .toList();

        return new AgencyDashboardResponse(
                dashboard.getAgency(),
                packages,
                topPackages,
                topPlaces,
                toSalesSummaryResponse(dashboard.getSalesSummary())
        );
    }

    public static AgencyTourPackageResponse toPackageResponse(TourPackage pkg) {
        List<String> includes = pkg.getIncludes() == null
                ? List.of()
                : Arrays.asList(pkg.getIncludes());

        List<Long> placeIds = pkg.getPlaceIds() == null
                ? List.of()
                : Arrays.asList(pkg.getPlaceIds());

        List<Place> places = pkg.getPlaces() == null
                ? List.of()
                : pkg.getPlaces();

        return new AgencyTourPackageResponse(
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
                includes,
                pkg.getImage(),
                placeIds,
                pkg.getAgencyId(),
                pkg.getAgencyName(),
                places
        );
    }

    public static TopPackageResponse toTopPackageResponse(TopPackage pkg) {
        return new TopPackageResponse(
                pkg.getPackageId(),
                pkg.getTitle(),
                pkg.getSold(),
                pkg.getRevenue()
        );
    }

    public static SalesSummaryResponse toSalesSummaryResponse(TourPackageSalesSummary summary) {
        if (summary == null) {
            return new SalesSummaryResponse(0L, 0L);
        }

        return new SalesSummaryResponse(
                summary.getTotalSold(),
                summary.getTotalRevenue()
        );
    }
}