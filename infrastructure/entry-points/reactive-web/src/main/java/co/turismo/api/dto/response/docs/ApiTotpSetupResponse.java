package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.auth.TotpSetupResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiTotpSetupResponse", description = "Devuelve el secreto y el QR generado para enrolar TOTP")
public record ApiTotpSetupResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        TotpSetupResponse data
) {
}
