package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payload para confirmar que la visita se completó correctamente")
public record ConfirmRequest(
        @Schema(description = "Latitud del usuario al confirmar", example = "6.25184")
        double lat,
        @Schema(description = "Longitud del usuario al confirmar", example = "-75.56359")
        double lng,
        @Schema(description = "Precisión reportada por el dispositivo en metros", example = "10")
        Integer accuracy_m
) {
}

