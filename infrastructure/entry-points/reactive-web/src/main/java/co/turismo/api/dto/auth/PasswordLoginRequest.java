package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PasswordLoginRequest", description = "Credenciales para login con contraseña")
public record PasswordLoginRequest(
        @Schema(example = "ana@example.com")
        String email,
        @Schema(example = "MiPasswordSegura123")
        String password
) {
}
