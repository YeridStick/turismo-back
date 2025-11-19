package co.turismo.api.dto.response.docs;

import co.turismo.model.geocode.GeocodeResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiGeocodeResponse", description = "Resultados posibles para la consulta de geocodificación")
public record ApiGeocodeResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @ArraySchema(
                schema = @Schema(implementation = GeocodeResult.class),
                arraySchema = @Schema(description = "Coincidencias ordenadas por confianza")
        )
        List<GeocodeResult> data
) {
}
