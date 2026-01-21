package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RecoveryConfirmRequest", description = "Confirma un codigo de recuperacion y reinicia el TOTP")
public record RecoveryConfirmRequest(
        @Schema(example = "ana@example.com")
        String email,
        @Schema(example = "123456")
        String code
) {
}
