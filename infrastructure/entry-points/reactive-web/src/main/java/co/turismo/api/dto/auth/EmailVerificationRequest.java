package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailVerificationRequest", description = "Solicitud para enviar correo de verificacion")
public record EmailVerificationRequest(
        @Schema(example = "ana@example.com")
        String email
) {
}
