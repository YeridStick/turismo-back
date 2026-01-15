package co.turismo.api.dto.response.docs;

import co.turismo.api.handler.AgencyHandler;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiAgencyDashboardResponse", description = "Envelope con métricas de la agencia")
public record ApiAgencyDashboardResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        AgencyHandler.AgencyDashboardResponse data
) {
}
