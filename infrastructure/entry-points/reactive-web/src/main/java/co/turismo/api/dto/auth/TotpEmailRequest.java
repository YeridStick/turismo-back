package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TotpSetupRequest", description = "Correo del usuario para iniciar el enrolamiento TOTP")
public record TotpEmailRequest(
        @Schema(description = "Correo electrónico normalizado", example = "ana@example.com")
        String email
) {
}
