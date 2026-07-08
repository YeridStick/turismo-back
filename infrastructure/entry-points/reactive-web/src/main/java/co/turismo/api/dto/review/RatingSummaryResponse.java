package co.turismo.api.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ReviewRatingSummary", description = "Resumen estadístico de calificaciones por lugar")
public record RatingSummaryResponse(
        @Schema(description = "Identificador del lugar consultado", example = "101")
        Long placeId,

        @Schema(description = "Promedio de calificaciones", example = "4.5")
        double avgRating,

        @Schema(description = "Número de reseñas consideradas", example = "245")
        long reviewsCount
) {}
