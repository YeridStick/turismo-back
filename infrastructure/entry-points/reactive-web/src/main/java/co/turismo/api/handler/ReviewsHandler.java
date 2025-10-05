package co.turismo.api.handler;

import co.turismo.model.reviews.Review;
import co.turismo.usecase.reviews.ReviewsUseCase;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ReviewsHandler {

    private final ReviewsUseCase reviews;

    @Data
    static class CreateReviewBody {
        private Short rating;
        private String comment;
    }

    /** GET público: lista reseñas por lugar */
    public Mono<ServerResponse> list(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("id"));
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(reviews.list(placeId), Review.class);
    }

    /** GET público: resumen rating de un lugar */
    public Mono<ServerResponse> summary(ServerRequest req) {
        Long placeId = Long.valueOf(req.pathVariable("id"));
        return reviews.summary(placeId)
                .flatMap(s -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "placeId", s.getPlaceId(),
                                "avgRating", s.getAvgRating(),
                                "reviewsCount", s.getReviewsCount()
                        )));
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
                    return reviews.create(placeId, b.getRating(), b.getComment(), email);
                })
                .flatMap(r -> ServerResponse.status(201)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(r))
                .onErrorResume(IllegalArgumentException.class,
                        e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())));
    }
}
