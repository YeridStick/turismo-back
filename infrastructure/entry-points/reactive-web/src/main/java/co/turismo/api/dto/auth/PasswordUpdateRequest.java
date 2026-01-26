package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PasswordUpdateRequest", description = "Solicitud para crear o actualizar contraseña")
public record PasswordUpdateRequest(
        @Schema(example = "MiPasswordSegura123")
        String password
) {
}
