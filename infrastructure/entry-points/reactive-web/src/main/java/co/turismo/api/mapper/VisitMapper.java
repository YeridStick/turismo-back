package co.turismo.api.mapper;

import co.turismo.api.dto.visit.CheckinRequest;
import co.turismo.api.dto.visit.CheckinResponse;
import co.turismo.api.dto.visit.ConfirmRequest;
import co.turismo.api.dto.visit.ConfirmResponse;
import co.turismo.api.dto.visit.PlaceBrief;
import co.turismo.api.dto.visit.PlaceNearbyDTO;
import co.turismo.api.dto.visit.TopPlaceDTO;
import co.turismo.api.dto.visit.UserFavoritePlaceDTO;
import co.turismo.model.visits.PlaceBriefUC;
import co.turismo.model.visits.PlaceNearby;
import co.turismo.model.visits.TopPlace;
import co.turismo.model.visits.UserFavoritePlace;
import co.turismo.usecase.visit.VisitsUseCase;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public final class VisitMapper {

    private VisitMapper() {}

    public static VisitsUseCase.CheckinCmd toCheckinCommand(Long placeId, CheckinRequest body, String email) {
        return new VisitsUseCase.CheckinCmd(
                placeId,
                body.lat(),
                body.lng(),
                body.accuracy_m(),
                body.device_id(),
                normalizeMeta(body.meta()),
                email
        );
    }

    public static VisitsUseCase.ConfirmCmd toConfirmCommand(Long visitId, ConfirmRequest body) {
        return new VisitsUseCase.ConfirmCmd(
                visitId,
                body.lat(),
                body.lng(),
                body.accuracy_m()
        );
    }

    public static CheckinResponse toCheckinResponse(VisitsUseCase.CheckinRes response) {
        return new CheckinResponse(
                response.visitId(),
                response.status(),
                response.minStaySeconds(),
                response.distanceM()
        );
    }

    public static ConfirmResponse toConfirmResponse(VisitsUseCase.ConfirmRes response) {
        return new ConfirmResponse(
                response.status(),
                response.confirmedAt(),
                toPlaceBrief(response.place())
        );
    }

    public static PlaceNearbyDTO toNearbyResponse(PlaceNearby place) {
        return new PlaceNearbyDTO(
                new PlaceBrief(
                        place.id(),
                        place.name(),
                        place.lat(),
                        place.lng(),
                        place.address(),
                        place.description(),
                        place.categoryId(),
                        safeImages(place.imageUrls())
                ),
                place.distanceM()
        );
    }

    public static TopPlaceDTO toTopPlaceResponse(TopPlace place) {
        return new TopPlaceDTO(
                place.getPlaceId(),
                place.getName(),
                place.getVisits()
        );
    }

    public static UserFavoritePlaceDTO toFavoriteResponse(UserFavoritePlace favorite) {
        return new UserFavoritePlaceDTO(
                new PlaceBrief(
                        favorite.getPlaceId(),
                        favorite.getName(),
                        favorite.getLat(),
                        favorite.getLng(),
                        favorite.getAddress(),
                        favorite.getDescription(),
                        favorite.getCategoryId(),
                        safeImages(favorite.getImageUrls())
                ),
                favorite.getFavoritedAt()
        );
    }

    private static PlaceBrief toPlaceBrief(PlaceBriefUC place) {
        return new PlaceBrief(
                place.id(),
                place.name(),
                place.lat(),
                place.lng(),
                place.address(),
                place.description(),
                place.categoryId(),
                safeImages(place.imageUrls())
        );
    }

    private static String normalizeMeta(JsonNode meta) {
        if (meta == null || meta.isNull()) {
            return "{}";
        }

        if (meta.isTextual()) {
            String value = meta.asText();
            return (value == null || value.isBlank()) ? "{}" : value;
        }

        return meta.toString();
    }

    private static List<String> safeImages(List<String> imageUrls) {
        return imageUrls == null ? List.of() : imageUrls;
    }
}
