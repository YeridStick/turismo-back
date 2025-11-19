package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.visit.TopPlaceDTO;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiTopPlacesResponse", description = "Ranking de los lugares con más visitas")
public record ApiTopPlacesResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @ArraySchema(
                schema = @Schema(implementation = TopPlaceDTO.class),
                arraySchema = @Schema(description = "Ranking ordenado por visitas")
        )
        List<TopPlaceDTO> data
) {
}
