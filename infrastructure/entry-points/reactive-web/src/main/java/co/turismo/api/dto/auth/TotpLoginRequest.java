package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TotpLoginRequest", description = "Credenciales mínimas para realizar login con TOTP")
public record TotpLoginRequest(
        @Schema(description = "Correo electrónico del usuario", example = "ana@example.com")
        String email,
        @Schema(description = "Código TOTP de 6 dígitos", example = "654321")
        int totpCode
) {
}
