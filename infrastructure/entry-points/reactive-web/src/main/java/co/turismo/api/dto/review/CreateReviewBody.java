package co.turismo.api.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateReviewRequest", description = "Información requerida para publicar una reseña")
public record CreateReviewBody(
        @Schema(description = "Calificación de 1 a 5", minimum = "1", maximum = "5", example = "5")
        Short rating,

        @Schema(description = "Comentario opcional del usuario", example = "Excelente atención y ambiente")
        String comment
) {}
