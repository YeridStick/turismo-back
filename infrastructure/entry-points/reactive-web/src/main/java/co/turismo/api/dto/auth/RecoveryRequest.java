package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RecoveryRequest", description = "Solicitud para enviar codigo de recuperacion")
public record RecoveryRequest(
        @Schema(example = "ana@example.com")
        String email
) {
}
