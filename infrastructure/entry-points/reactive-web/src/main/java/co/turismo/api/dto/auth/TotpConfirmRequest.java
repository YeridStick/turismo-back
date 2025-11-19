package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TotpConfirmRequest", description = "Código inicial para confirmar y activar TOTP")
public record TotpConfirmRequest(
        @Schema(description = "Correo electrónico del usuario", example = "ana@example.com")
        String email,
        @Schema(description = "Código TOTP de 6 dígitos", example = "123456", minimum = "000000", maximum = "999999")
        int code
) {
}
