package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TotpStatusResponse", description = "Indica si el usuario ya tiene TOTP habilitado")
public record TotpStatusResponse(
        @Schema(description = "true si el usuario ya tiene un secreto confirmado", example = "true")
        boolean enabled
) {
}
