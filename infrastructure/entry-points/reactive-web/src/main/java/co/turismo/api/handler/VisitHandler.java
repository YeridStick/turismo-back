package co.turismo.api.handler;

import co.turismo.api.dto.visit.*;
import co.turismo.api.http.HttpResponses;
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

    private final VisitsUseCase visitsUseCase;

    // POST /api/places/{placeId}/checkin
    public Mono<ServerResponse> checkin(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("placeId"));

        // Si hay principal, toma el email; si no, usa "" (evita NPE en defaultIfEmpty)
        Mono<String> emailMono = req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .onErrorResume(e -> Mono.empty())
                .defaultIfEmpty(""); // <- ¡clave!

        return req.bodyToMono(CheckinRequest.class)
                .zipWith(emailMono)
                .flatMap(tuple -> {
                    CheckinRequest body = tuple.getT1();
                    String email = tuple.getT2(); // "" si no hay sesión

                    // Si luego expones userId desde el JWT, mapéalo aquí.
                    Long userId = null;

                    var cmd = new VisitsUseCase.CheckinCmd(
                            placeId,
                            body.lat(), body.lng(),
                            body.accuracy_m(),
                            body.device_id(),
                            body.meta() != null ? body.meta() : "{}",
                            userId
                    );
                    return visitsUseCase.checkin(cmd);
                })
                .map(r -> new CheckinResponse(r.visitId(), r.status(), r.minStaySeconds(), r.distanceM()))
                .flatMap(HttpResponses::ok);
    }


    // PATCH /api/visits/{visitId}/confirm
    public Mono<ServerResponse> confirm(ServerRequest req) {
        Long visitId = Long.valueOf(req.pathVariable("visitId"));
        return req.bodyToMono(ConfirmRequest.class)
                .flatMap(b -> visitsUseCase.confirm(new VisitsUseCase.ConfirmCmd(
                        visitId, b.lat(), b.lng(), b.accuracy_m()
                )))
                .map(r -> new ConfirmResponse(r.status(), r.confirmedAt()))
                .flatMap(HttpResponses::ok);
    }

    // GET /api/analytics/places/top?from&to&limit
    public Mono<ServerResponse> topPlaces(ServerRequest req) {
        LocalDate from = req.queryParam("from")
                .map(LocalDate::parse)
                .orElse(LocalDate.now().minusDays(30));
        LocalDate to = req.queryParam("to")
                .map(LocalDate::parse)
                .orElse(LocalDate.now());
        int limit = Integer.parseInt(req.queryParam("limit").orElse("20"));

        return visitsUseCase.top(from, to, limit)
                .map(tp -> new TopPlaceDTO(tp.getPlaceId(), tp.getName(), tp.getVisits()))
                .collectList()
                .flatMap(HttpResponses::ok);
    }
}
