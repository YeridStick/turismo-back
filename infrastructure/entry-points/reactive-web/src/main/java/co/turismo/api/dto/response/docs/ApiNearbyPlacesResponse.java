package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.visit.PlaceNearbyDTO;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiNearbyPlacesResponse", description = "Lista de lugares cercanos a partir de la ubicación del usuario")
public record ApiNearbyPlacesResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @ArraySchema(
                schema = @Schema(implementation = PlaceNearbyDTO.class),
                arraySchema = @Schema(description = "Lugares ordenados por distancia")
        )
        List<PlaceNearbyDTO> data
) {
}
