package co.turismo.api.handler;

import co.turismo.api.dto.visit.*;
import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.http.HttpResponses;
import co.turismo.usecase.visit.VisitsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class VisitHandler {

    private static final int MAX_LIMIT = 50;

    private final VisitsUseCase visitsUseCase;

    // POST /api/places/{id}/checkin
    public Mono<ServerResponse> checkin(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("id"));

        Mono<String> emailMono = authenticatedEmail(req);

        return req.bodyToMono(CheckinRequest.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Body requerido")))
                .zipWith(emailMono)
                .flatMap(tuple -> {
                    CheckinRequest body = tuple.getT1();
                    String email = tuple.getT2();

                    var cmd = new VisitsUseCase.CheckinCmd(
                            placeId,
                            body.lat(), body.lng(),
                            body.accuracy_m(),
                            body.device_id(),
                            normalizeMeta(body.meta()),
                            email // ← ahora pasamos email, no userId
                    );

                    return visitsUseCase.checkin(cmd);
                })
                .map(r -> new CheckinResponse(r.visitId(), r.status(), r.minStaySeconds(), r.distanceM()))
                .flatMap(HttpResponses::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> HttpResponses.badRequest(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> HttpResponses.conflict(e.getMessage()));
    }

    private static String normalizeMeta(com.fasterxml.jackson.databind.JsonNode meta) {
        if (meta == null || meta.isNull()) return "{}";
        if (meta.isTextual()) {
            String value = meta.asText();
            return (value == null || value.isBlank()) ? "{}" : value;
        }
        return meta.toString();
    }

    // PATCH /api/visits/{visitId}/confirm
    public Mono<ServerResponse> confirm(ServerRequest req) {
        Long visitId = Long.valueOf(req.pathVariable("visitId"));
        return req.bodyToMono(ConfirmRequest.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Body requerido")))
                .flatMap(b -> visitsUseCase.confirm(new VisitsUseCase.ConfirmCmd(
                        visitId, b.lat(), b.lng(), b.accuracy_m()
                )))
                .map(r -> new ConfirmResponse(
                        r.status(),
                        r.confirmedAt(),
                        new PlaceBrief(
                                r.place().id(),
                                r.place().name(),
                                r.place().lat(),   // primero lat
                                r.place().lng(),   // luego lng
                                r.place().address(),
                                r.place().description(),
                                r.place().categoryId(),
                                r.place().imageUrls()
                        )
                ))
                .flatMap(HttpResponses::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> HttpResponses.badRequest(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> HttpResponses.conflict(e.getMessage()));
    }

    // GET /api/places/nearby/getpalce?lat&lng&radius&limit
    public Mono<ServerResponse> nearby(ServerRequest req) {
        double lat = Double.parseDouble(req.queryParam("lat").orElseThrow());
        double lng = Double.parseDouble(req.queryParam("lng").orElseThrow());
        int radius = Integer.parseInt(req.queryParam("radius").orElse("150"));
        int limit  = Math.min(MAX_LIMIT, Math.max(1, Integer.parseInt(req.queryParam("limit").orElse("1"))));

        return visitsUseCase.nearby(lat, lng, radius, limit)
                .map(r -> new PlaceNearbyDTO(
                        new PlaceBrief(
                                r.id(),
                                r.name(),
                                r.lat(),
                                r.lng(),
                                r.address(),
                                r.description(),
                                r.categoryId(),
                                r.imageUrls() == null ? List.of() : r.imageUrls()
                        ),
                        r.distanceM()
                ))
                .collectList()
                .flatMap(HttpResponses::ok);
    }

    // GET /api/pruebas/analytics/places/top?from&to&limit
    public Mono<ServerResponse> topPlaces(ServerRequest req) {
        LocalDate from = req.queryParam("from")
                .map(LocalDate::parse)
                .orElse(LocalDate.now().minusDays(30));
        LocalDate to = req.queryParam("to")
                .map(LocalDate::parse)
                .orElse(LocalDate.now());
        int limit = Math.min(MAX_LIMIT, Math.max(1, Integer.parseInt(req.queryParam("limit").orElse("20"))));

        return visitsUseCase.top(from, to, limit)
                .map(tp -> new TopPlaceDTO(tp.getPlaceId(), tp.getName(), tp.getVisits()))
                .collectList()
                .flatMap(HttpResponses::ok);
    }

    // GET /api/pruebas/users/me/visits/top?limit=10
    public Mono<ServerResponse> myTopVisited(ServerRequest req) {
        int limit = Math.min(MAX_LIMIT, Math.max(1, Integer.parseInt(req.queryParam("limit").orElse("10"))));
        return authenticatedEmail(req)
                .flatMapMany(email -> visitsUseCase.myTopVisited(email, limit))
                .map(tp -> new TopPlaceDTO(tp.getPlaceId(), tp.getName(), tp.getVisits()))
                .collectList()
                .flatMap(HttpResponses::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> HttpResponses.badRequest(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> HttpResponses.conflict(e.getMessage()));
    }

    // GET /api/pruebas/users/me/favorites?limit=20&offset=0
    public Mono<ServerResponse> myFavorites(ServerRequest req) {
        int limit = Math.min(MAX_LIMIT, Math.max(1, Integer.parseInt(req.queryParam("limit").orElse("20"))));
        int offset = Math.max(0, Integer.parseInt(req.queryParam("offset").orElse("0")));

        return authenticatedEmail(req)
                .flatMapMany(email -> visitsUseCase.myFavorites(email, limit, offset))
                .map(f -> new UserFavoritePlaceDTO(
                        new PlaceBrief(
                                f.getPlaceId(),
                                f.getName(),
                                f.getLat(),
                                f.getLng(),
                                f.getAddress(),
                                f.getDescription(),
                                f.getCategoryId(),
                                f.getImageUrls() == null ? List.of() : f.getImageUrls()
                        ),
                        f.getFavoritedAt()
                ))
                .collectList()
                .flatMap(HttpResponses::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> HttpResponses.badRequest(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> HttpResponses.conflict(e.getMessage()));
    }

    // POST /api/pruebas/users/me/favorites/{placeId}
    public Mono<ServerResponse> addFavorite(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("placeId"));
        return authenticatedEmail(req)
                .flatMap(email -> visitsUseCase.addFavorite(email, placeId))
                .then(HttpResponses.ok(new SimpleMessageResponse("Favorito agregado")))
                .onErrorResume(IllegalArgumentException.class,
                        e -> HttpResponses.badRequest(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> HttpResponses.conflict(e.getMessage()));
    }

    // DELETE /api/pruebas/users/me/favorites/{placeId}
    public Mono<ServerResponse> removeFavorite(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("placeId"));
        return authenticatedEmail(req)
                .flatMap(email -> visitsUseCase.removeFavorite(email, placeId))
                .then(HttpResponses.ok(new SimpleMessageResponse("Favorito eliminado")))
                .onErrorResume(IllegalArgumentException.class,
                        e -> HttpResponses.badRequest(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> HttpResponses.conflict(e.getMessage()));
    }

    private static Mono<String> authenticatedEmail(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no autenticado")));
    }
}
