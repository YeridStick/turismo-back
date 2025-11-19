package co.turismo.api.dto.response.docs;

import co.turismo.model.place.Place;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiPlaceResponse", description = "Envelope con la entidad completa del lugar")
public record ApiPlaceResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        Place data
) {
}
