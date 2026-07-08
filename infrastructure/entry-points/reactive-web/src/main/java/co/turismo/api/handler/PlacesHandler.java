package co.turismo.api.handler;

import co.turismo.api.dto.place.ActiveRequest;
import co.turismo.api.dto.place.PlaceCreateRequest;
import co.turismo.api.dto.place.UpdateRequest;
import co.turismo.api.dto.place.VerifyRequest;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.api.mapper.PlaceMapper;
import co.turismo.model.place.Place;
import co.turismo.usecase.place.PlaceUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.file.AccessDeniedException;

@Component
@RequiredArgsConstructor
public class PlacesHandler {

    private static final int MAX_SIZE = 50;

    private final PlaceUseCase placeUseCase;

    public Mono<ServerResponse> create(ServerRequest req) {
        return authenticatedEmail(req)
                .zipWith(requiredBody(req, PlaceCreateRequest.class)
                        .flatMap(PlacesHandler::validateCreateBody))
                .map(tuple -> PlaceMapper.toCreatePlaceRequest(tuple.getT1(), tuple.getT2()))
                .flatMap(placeUseCase::createPlace)
                .flatMap(place -> created(
                        req.uriBuilder()
                                .path("/{id}")
                                .build(place.getId()),
                        place
                ))
                .onErrorResume(IllegalArgumentException.class, PlacesHandler::badRequest);
    }

    public Mono<ServerResponse> patch(ServerRequest req) {
        return parsePathId(req, "id")
                .zipWith(requiredBody(req, UpdateRequest.class)
                        .flatMap(PlacesHandler::validateUpdateBody)
                        .map(PlaceMapper::toUpdatePlaceRequest))
                .flatMap(tuple -> placeUseCase.patch(tuple.getT1(), tuple.getT2()))
                .flatMap(PlacesHandler::ok)
                .onErrorResume(IllegalArgumentException.class, PlacesHandler::badRequest)
                .onErrorResume(ResponseStatusException.class, PlacesHandler::statusError);
    }

    public Mono<ServerResponse> findByIdPlace(ServerRequest req) {
        return parsePathId(req, "id")
                .flatMap(placeUseCase::findByIdPlace)
                .switchIfEmpty(notFound("Lugar no encontrado"))
                .flatMap(PlacesHandler::ok)
                .onErrorResume(ResponseStatusException.class, PlacesHandler::statusError);
    }

    public Mono<ServerResponse> searchFilterPlace(ServerRequest request) {
        return Mono.fromSupplier(() -> PlaceMapper.toSearchCriteria(request, MAX_SIZE))
                .flatMapMany(placeUseCase::searchPlace)
                .collectList()
                .flatMap(PlacesHandler::ok)
                .onErrorResume(IllegalArgumentException.class, PlacesHandler::badRequest);
    }

    public Mono<ServerResponse> verify(ServerRequest req) {
        return parsePathId(req, "id")
                .flatMap(placeId -> authenticatedEmail(req)
                        .zipWith(requiredBody(req, VerifyRequest.class))
                        .flatMap(tuple -> placeUseCase.verifyPlaceByAdmin(
                                tuple.getT1(),
                                placeId,
                                tuple.getT2().approve()
                        )))
                .flatMap(PlacesHandler::ok)
                .onErrorResume(ResponseStatusException.class, PlacesHandler::statusError);
    }

    public Mono<ServerResponse> setActive(ServerRequest req) {
        return parsePathId(req, "id")
                .flatMap(placeId -> authenticatedEmail(req)
                        .zipWith(requiredBody(req, ActiveRequest.class))
                        .flatMap(tuple -> placeUseCase.setActiveByOwner(
                                tuple.getT1(),
                                placeId,
                                tuple.getT2().active()
                        )))
                .flatMap(PlacesHandler::ok)
                .onErrorResume(ResponseStatusException.class, PlacesHandler::statusError);
    }

    public Mono<ServerResponse> myPlaces(ServerRequest req) {
        return Mono.fromSupplier(() -> new Pagination(
                        parseLimit(req.queryParam("limit").orElse(null), 10),
                        parseOffset(req.queryParam("offset").orElse(null), 0)
                ))
                .flatMapMany(pagination -> authenticatedEmail(req)
                        .flatMapMany(email -> placeUseCase.findMine(
                                email,
                                pagination.limit(),
                                pagination.offset()
                        )))
                .collectList()
                .flatMap(PlacesHandler::ok)
                .onErrorResume(IllegalArgumentException.class, PlacesHandler::badRequest);
    }

    public Mono<ServerResponse> delete(ServerRequest req) {
        return parsePathId(req, "id")
                .flatMap(placeId -> authenticatedEmail(req)
                        .flatMap(email -> placeUseCase.deleteByOwnerOrAdmin(email, placeId)))
                .flatMap(PlacesHandler::ok)
                .onErrorResume(AccessDeniedException.class, error ->
                        error(403, error.getMessage())
                )
                .onErrorResume(ResponseStatusException.class, PlacesHandler::statusError)
                .onErrorResume(PlacesHandler::fallbackError);
    }

    public Mono<ServerResponse> debug(ServerRequest req) {
        return authenticatedEmail(req)
                .flatMap(email -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .bodyValue("Email: " + email));
    }

    private static Mono<PlaceCreateRequest> validateCreateBody(PlaceCreateRequest body) {
        if (!hasText(body.name())) {
            return Mono.error(new IllegalArgumentException("name es obligatorio"));
        }

        if (!hasText(body.description())) {
            return Mono.error(new IllegalArgumentException("description es obligatorio"));
        }

        if (body.categoryId() == null) {
            return Mono.error(new IllegalArgumentException("categoryId es obligatorio"));
        }

        if (body.lat() == null || body.lng() == null) {
            return Mono.error(new IllegalArgumentException("lat y lng son obligatorios"));
        }

        return Mono.just(body);
    }

    private static Mono<UpdateRequest> validateUpdateBody(UpdateRequest body) {
        boolean hasAnyField = hasText(body.name())
                || hasText(body.description())
                || body.categoryId() != null
                || body.lat() != null
                || body.lng() != null
                || hasText(body.address())
                || hasText(body.phone())
                || hasText(body.website())
                || body.imageUrls() != null
                || body.model3dUrls() != null
                || body.services() != null;

        if (!hasAnyField) {
            return Mono.error(new IllegalArgumentException("Debes enviar al menos un campo"));
        }

        return Mono.just(body);
    }

    private static Mono<String> authenticatedEmail(ServerRequest req) {
        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)
                .filter(PlacesHandler::hasText)
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no autenticado")));
    }

    private static <T> Mono<T> requiredBody(ServerRequest req, Class<T> bodyType) {
        return req.bodyToMono(bodyType)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Body requerido")));
    }

    private static Mono<Long> parsePathId(ServerRequest req, String variableName) {
        return Mono.fromSupplier(() -> Long.parseLong(req.pathVariable(variableName)))
                .onErrorMap(NumberFormatException.class, error ->
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                variableName + " inválido"
                        ));
    }

    private static int parseLimit(String value, int fallback) {
        if (!hasText(value)) {
            return fallback;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.min(MAX_SIZE, Math.max(1, parsed));
        } catch (Exception error) {
            throw new IllegalArgumentException("limit inválido");
        }
    }

    private static int parseOffset(String value, int fallback) {
        if (!hasText(value)) {
            return fallback;
        }

        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception error) {
            throw new IllegalArgumentException("offset inválido");
        }
    }

    private static Mono<ServerResponse> ok(Object body) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.ok(body));
    }

    private static Mono<ServerResponse> created(URI location, Object body) {
        return ServerResponse.created(location)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.created(body));
    }

    private static Mono<ServerResponse> badRequest(IllegalArgumentException error) {
        return error(400, safeMessage(error, "Solicitud inválida"));
    }

    private static Mono<ServerResponse> statusError(ResponseStatusException error) {
        return error(
                error.getStatusCode().value(),
                safeMessage(error, "Error procesando solicitud")
        );
    }

    private static Mono<ServerResponse> fallbackError(Throwable error) {
        String message = safeMessage(error, "Error");

        if (message.toLowerCase().contains("no encontrado")) {
            return error(404, message);
        }

        return error(400, message);
    }

    private static Mono<ServerResponse> error(int status, String message) {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ApiResponse.error(status, message));
    }

    private static <T> Mono<T> notFound(String message) {
        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, message));
    }

    private static String safeMessage(Throwable error, String fallback) {
        String message = error != null ? error.getMessage() : null;
        return hasText(message) ? message : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Pagination(
            int limit,
            int offset
    ) {
    }
}