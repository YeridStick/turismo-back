package co.turismo.api.dto.geocode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GeocodeRequest", description = "Dirección que se desea transformar en coordenadas")
public record GeocodeRequest(
        @Schema(description = "Texto completo de la dirección", example = "Cra. 7 #40-62, Bogotá")
        String address,

        @Schema(description = "Máximo de resultados a retornar (1-100)", example = "5")
        Integer limit
) {}
