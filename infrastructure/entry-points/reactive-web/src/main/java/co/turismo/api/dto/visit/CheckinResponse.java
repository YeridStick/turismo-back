package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado inmediato del check-in")
public record CheckinResponse(
        @Schema(description = "Identificador interno de la visita", example = "98127")
        long visit_id,
        @Schema(description = "Estado del check-in", example = "PENDING")
        String status,
        @Schema(description = "Tiempo mínimo en segundos para validar la visita", example = "180")
        int min_stay_seconds,
        @Schema(description = "Distancia al lugar en metros", example = "12")
        int distance_m
) {
}

