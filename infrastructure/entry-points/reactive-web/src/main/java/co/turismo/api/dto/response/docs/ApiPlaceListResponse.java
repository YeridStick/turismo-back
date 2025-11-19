package co.turismo.api.dto.response.docs;

import co.turismo.model.place.Place;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiPlaceListResponse", description = "Lista de lugares dentro del envelope estándar")
public record ApiPlaceListResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        @ArraySchema(
                schema = @Schema(implementation = Place.class),
                arraySchema = @Schema(description = "Listado paginado o filtrado de lugares")
        )
        List<Place> data
) {
}
