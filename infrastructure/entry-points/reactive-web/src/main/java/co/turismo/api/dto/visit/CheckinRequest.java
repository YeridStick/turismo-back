package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos recopilados desde el dispositivo móvil para iniciar un check-in")
public record CheckinRequest(
        @Schema(description = "Latitud decimal del visitante", example = "6.25184", requiredMode = Schema.RequiredMode.REQUIRED)
        double lat,
        @Schema(description = "Longitud decimal del visitante", example = "-75.56359", requiredMode = Schema.RequiredMode.REQUIRED)
        double lng,
        @Schema(description = "Precisión del GPS en metros", example = "15")
        Integer accuracy_m,
        @Schema(description = "Identificador del dispositivo", example = "android-device-123")
        String device_id,
        @Schema(description = "Payload adicional en JSON", example = "{\"appVersion\":\"2.3.1\"}")
        String meta
) {
}

