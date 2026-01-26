package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmailVerificationResponse", description = "Resultado del envio de verificacion de correo")
public record EmailVerificationResponse(
        @Schema(example = "sent")
        String status,
        @Schema(example = "Correo de verificacion enviado")
        String message
) {
}
