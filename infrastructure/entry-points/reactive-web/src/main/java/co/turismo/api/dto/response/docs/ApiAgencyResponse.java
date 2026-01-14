package co.turismo.api.dto.response.docs;

import co.turismo.model.agency.Agency;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiAgencyResponse", description = "Envelope con la agencia")
public record ApiAgencyResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        Agency data
) {
}
