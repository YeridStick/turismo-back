package co.turismo.api.dto.visit;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Elemento del ranking de lugares con más visitas")
public record TopPlaceDTO(
        @Schema(description = "Identificador del lugar", example = "101")
        Long place_id,
        @Schema(description = "Nombre comercial", example = "Café del Parque")
        String name,
        @Schema(description = "Cantidad de visitas confirmadas en la ventana consultada", example = "254")
        Integer visits
) {
}

