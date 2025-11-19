package co.turismo.api.handler;

import co.turismo.api.dto.response.ApiResponse;
import co.turismo.model.reviews.Review;
import co.turismo.usecase.reviews.ReviewsUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(name = "CreateReviewRequest", description = "Información requerida para publicar una reseña")
    public static class CreateReviewBody {
        @Schema(description = "Calificación de 1 a 5", minimum = "1", maximum = "5", example = "5")
        private Short rating;
        @Schema(description = "Comentario opcional del usuario", example = "Excelente atención y ambiente")
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
                        .bodyValue(new RatingSummaryResponse(s.getPlaceId(), s.getAvgRating(), s.getReviewsCount())));
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
                        e -> ServerResponse.badRequest().bodyValue(ApiResponse.error(400, e.getMessage())));
    }

    @Schema(name = "ReviewRatingSummary", description = "Resumen estadístico de calificaciones por lugar")
    public record RatingSummaryResponse(
            @Schema(description = "Identificador del lugar consultado", example = "101")
            Long placeId,
            @Schema(description = "Promedio de calificaciones", example = "4.5")
            double avgRating,
            @Schema(description = "Número de reseñas consideradas", example = "245")
            long reviewsCount
    ) {
    }
}
