package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.visit.ConfirmResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiConfirmVisitResponse", description = "Respuesta al confirmar una visita")
public record ApiConfirmResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        ConfirmResponse data
) {
}
