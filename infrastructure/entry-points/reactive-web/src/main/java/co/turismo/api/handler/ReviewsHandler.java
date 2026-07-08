package co.turismo.api.handler;

import co.turismo.api.dto.review.CreateReviewBody;
import co.turismo.api.dto.review.RatingSummaryResponse;
import co.turismo.api.dto.response.ApiResponse;
import co.turismo.usecase.reviews.ReviewsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ReviewsHandler {

    private static final int MAX_LIMIT = 50;

    private final ReviewsUseCase reviews;

    /** GET público: lista reseñas por lugar con paginación */
    public Mono<ServerResponse> list(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("id"));
        int limit = req.queryParam("limit")
                .map(Integer::parseInt)
                .map(v -> Math.min(MAX_LIMIT, Math.max(1, v)))
                .orElse(20);
        int offset = req.queryParam("offset")
                .map(Integer::parseInt)
                .orElse(0);
        return reviews.listPaginated(placeId, limit, offset)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    /** GET público: resumen rating de un lugar */
    public Mono<ServerResponse> summary(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("id"));
        return reviews.summary(placeId)
                .flatMap(s -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(new RatingSummaryResponse(s.getPlaceId(), s.getAvgRating(), s.getReviewsCount())));
    }

    /** GET público: top lugares con mejores reseñas */
    public Mono<ServerResponse> topRatedPlaces(ServerRequest req) {
        int limit = req.queryParam("limit")
                .map(Integer::parseInt)
                .map(v -> Math.min(MAX_LIMIT, Math.max(1, v)))
                .orElse(3);

        return reviews.topRatedPlaceFlux(limit)
                .collectList()
                .flatMap(list -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ApiResponse.ok(list)));
    }

    /** POST protegido: crea reseña con email autenticado */
    public Mono<ServerResponse> create(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("id"));

        return req.principal()
                .cast(Authentication.class)
                .map(Authentication::getName)  // ← email del usuario
                .switchIfEmpty(Mono.error(new IllegalStateException("Usuario no autenticado")))
                .zipWith(req.bodyToMono(CreateReviewBody.class)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Body requerido"))))
                .flatMap(t -> {
                    String email = t.getT1();
                    CreateReviewBody b = t.getT2();
                    return reviews.create(placeId, b.rating(), b.comment(), email);
                })
                .flatMap(r -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(r))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest().bodyValue(ApiResponse.error(400, e.getMessage())));
    }

}
