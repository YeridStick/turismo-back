package co.turismo.api.dto.response.docs;

import co.turismo.model.agency.Agency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ApiAgencyListResponse", description = "Envelope con listado de agencias")
public record ApiAgencyListResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        List<Agency> data
) {
}
