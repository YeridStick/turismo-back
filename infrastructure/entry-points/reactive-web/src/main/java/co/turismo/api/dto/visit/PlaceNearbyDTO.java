package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lugar cercano sugerido al usuario durante el flujo de visitas")
public record PlaceNearbyDTO(
        @Schema(description = "Lugar sugerido con información básica")
        PlaceBrief place,
        @Schema(description = "Distancia aproximada en metros", example = "35")
        Integer distanceM
) {
}
