package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RecoveryConfirmRequest", description = "Confirma un token de recuperacion y reinicia el TOTP")
public record RecoveryConfirmRequest(
        @Schema(example = "of2wM9Ai7koygak6GHxQL4N3luKjXBjPaHPG85Kw2vI")
        String token,
        @Schema(example = "MiPasswordSegura123")
        String newPassword
) {
}
