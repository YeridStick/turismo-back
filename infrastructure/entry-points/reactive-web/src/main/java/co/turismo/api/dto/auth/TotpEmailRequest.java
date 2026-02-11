package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TotpSetupRequest", description = "Datos para iniciar el enrolamiento TOTP")
public record TotpEmailRequest(
        @Schema(description = "Correo electronico normalizado", example = "ana@example.com")
        String email,

        @Schema(description = "Contrasena actual del usuario para validar identidad", example = "P4ssw0rd!")
        String password
) {
}
