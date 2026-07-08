package co.turismo.api.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "TopRatedPlaceResponse",
        description = "Información resumida de los lugares mejor calificados"
)
public record TopRatedPlaceResponse(
        @Schema(description = "Identificador único del lugar", example = "15")
        Long id,

        @Schema(description = "Nombre del lugar turístico", example = "La Mano del Gigante")
        String name,

        @Schema(
                description = "Descripción corta del lugar",
                example = "Mirador turístico reconocido por su vista panorámica y estructura icónica."
        )
        String description,

        @Schema(description = "Promedio total de calificaciones recibidas", example = "4.8")
        double avgRating,

        @Schema(description = "Cantidad total de reseñas registradas", example = "312")
        long reviewsCount
) {}
