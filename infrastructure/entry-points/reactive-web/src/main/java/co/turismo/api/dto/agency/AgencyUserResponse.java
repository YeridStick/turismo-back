package co.turismo.api.dto.agency;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AgencyUserResponse", description = "Información básica de un usuario vinculado")
public record AgencyUserResponse(
        @Schema(description = "ID del usuario", example = "5")
        Long id,
        @Schema(description = "Email del usuario", example = "usuario@correo.com")
        String email
) {
}
