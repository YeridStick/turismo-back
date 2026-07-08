package co.turismo.api.handler;

import co.turismo.api.dto.visit.*;
import co.turismo.api.dto.common.SimpleMessageResponse;
import co.turismo.api.http.HttpResponses;
import co.turismo.api.mapper.VisitMapper;
import co.turismo.usecase.visit.VisitsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
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

                    return visitsUseCase.checkin(VisitMapper.toCheckinCommand(placeId, body, email));
                })
                .map(VisitMapper::toCheckinResponse)
                .flatMap(HttpResponses::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> HttpResponses.badRequest(e.getMessage()))
                .onErrorResume(IllegalStateException.class,
                        e -> HttpResponses.conflict(e.getMessage()));
    }

    // PATCH /api/visits/{visitId}/confirm
    public Mono<ServerResponse> confirm(ServerRequest req) {
        Long visitId = Long.valueOf(req.pathVariable("visitId"));
        return req.bodyToMono(ConfirmRequest.class)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Body requerido")))
                .map(b -> VisitMapper.toConfirmCommand(visitId, b))
                .flatMap(visitsUseCase::confirm)
                .map(VisitMapper::toConfirmResponse)
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
                .map(VisitMapper::toNearbyResponse)
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
                .map(VisitMapper::toTopPlaceResponse)
                .collectList()
                .flatMap(HttpResponses::ok);
    }

    // GET /api/pruebas/users/me/visits/top?limit=10
    public Mono<ServerResponse> myTopVisited(ServerRequest req) {
        int limit = Math.min(MAX_LIMIT, Math.max(1, Integer.parseInt(req.queryParam("limit").orElse("10"))));
        return authenticatedEmail(req)
                .flatMapMany(email -> visitsUseCase.myTopVisited(email, limit))
                .map(VisitMapper::toTopPlaceResponse)
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
                .map(VisitMapper::toFavoriteResponse)
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
