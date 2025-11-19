package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.auth.TotpStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiTotpStatusResponse", description = "Respuesta para consultar si un usuario ya tiene TOTP")
public record ApiTotpStatusResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        TotpStatusResponse data
) {
}
