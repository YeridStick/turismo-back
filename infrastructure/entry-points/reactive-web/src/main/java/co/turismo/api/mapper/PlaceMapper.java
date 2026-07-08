package co.turismo.api.mapper;

import co.turismo.api.dto.place.PlaceCreateRequest;
import co.turismo.api.dto.place.UpdateRequest;
import co.turismo.model.place.CreatePlaceRequest;
import co.turismo.model.place.UpdatePlaceRequest;
import co.turismo.model.place.strategy.PlaceSearchCriteria;
import co.turismo.model.place.strategy.PlaceSearchMode;
import org.springframework.web.reactive.function.server.ServerRequest;

public final class PlaceMapper {

    private static final int DEFAULT_SIZE = 10;
    private static final int DEFAULT_PAGE = 0;

    private PlaceMapper() {}

    public static CreatePlaceRequest toCreatePlaceRequest(String ownerEmail, PlaceCreateRequest body) {
        return CreatePlaceRequest.builder()
                .ownerEmail(ownerEmail)
                .name(safe(body.name()))
                .description(safe(body.description()))
                .categoryId(body.categoryId())
                .lat(body.lat())
                .lng(body.lng())
                .address(safe(body.address()))
                .phone(safe(body.phone()))
                .website(safe(body.website()))
                .imageUrls(body.imageUrls())
                .model3dUrls(body.model3dUrls())
                .services(body.services())
                .build();
    }

    public static UpdatePlaceRequest toUpdatePlaceRequest(UpdateRequest body) {
        return UpdatePlaceRequest.builder()
                .name(safe(body.name()))
                .description(safe(body.description()))
                .categoryId(body.categoryId())
                .lat(body.lat())
                .lng(body.lng())
                .address(safe(body.address()))
                .phone(safe(body.phone()))
                .website(safe(body.website()))
                .imageUrls(body.imageUrls() == null ? null : body.imageUrls().toArray(String[]::new))
                .model3dUrls(body.model3dUrls() == null ? null : body.model3dUrls().toArray(String[]::new))
                .services(body.services() == null ? null : body.services().toArray(String[]::new))
                .build();
    }

    public static PlaceSearchCriteria toSearchCriteria(ServerRequest request, int maxSize) {
        int size = request.queryParam("size")
                .map(value -> parseInt(value, "size inválido"))
                .map(value -> Math.min(maxSize, Math.max(1, value)))
                .orElse(DEFAULT_SIZE);

        int page = request.queryParam("page")
                .map(value -> parseInt(value, "page inválido"))
                .map(value -> Math.max(0, value))
                .orElse(DEFAULT_PAGE);

        return PlaceSearchCriteria.builder()
                .mode(request.queryParam("mode")
                        .map(PlaceMapper::parseSearchMode)
                        .orElse(PlaceSearchMode.ALL))
                .q(request.queryParam("q").map(PlaceMapper::safe).orElse(null))
                .categoryId(request.queryParam("categoryId")
                        .map(value -> parseLong(value, "categoryId inválido"))
                        .orElse(null))
                .lat(request.queryParam("lat")
                        .map(value -> parseDouble(value, "lat inválido"))
                        .orElse(null))
                .lng(request.queryParam("lng")
                        .map(value -> parseDouble(value, "lng inválido"))
                        .orElse(null))
                .radiusMeters(request.queryParam("radius")
                        .map(value -> parseDouble(value, "radius inválido"))
                        .orElse(null))
                .onlyNearby(request.queryParam("onlyNearby")
                        .map(value -> parseBoolean(value, "onlyNearby inválido"))
                        .orElse(false))
                .page(page)
                .size(size)
                .build();
    }

    private static PlaceSearchMode parseSearchMode(String value) {
        try {
            return PlaceSearchMode.valueOf(value.trim().toUpperCase());
        } catch (Exception error) {
            throw new IllegalArgumentException("mode inválido");
        }
    }

    private static Integer parseInt(String value, String message) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception error) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Long parseLong(String value, String message) {
        try {
            return Long.valueOf(value.trim());
        } catch (Exception error) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Double parseDouble(String value, String message) {
        try {
            return Double.valueOf(value.trim());
        } catch (Exception error) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Boolean parseBoolean(String value, String message) {
        String normalized = value == null ? "" : value.trim().toLowerCase();

        if ("true".equals(normalized)) {
            return true;
        }

        if ("false".equals(normalized)) {
            return false;
        }

        throw new IllegalArgumentException(message);
    }

    private static String safe(String value) {
        return value == null ? null : value.trim();
    }
}