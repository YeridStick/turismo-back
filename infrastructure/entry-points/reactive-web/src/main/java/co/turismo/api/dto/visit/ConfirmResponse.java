package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Detalle del resultado al confirmar una visita")
public record ConfirmResponse(
        @Schema(description = "Estado final del check-in", example = "CONFIRMED")
        String status,
        @Schema(description = "Instante de confirmación en ISO-8601", example = "2024-11-15T14:05:00Z")
        Instant confirmedAt,
        @Schema(description = "Información resumida del lugar visitado")
        PlaceBrief place
) {
}
