package co.turismo.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PasswordUpdateResponse", description = "Resultado de creación o actualización de contraseña")
public record PasswordUpdateResponse(
        @Schema(example = "created")
        String status,
        @Schema(example = "Contraseña configurada")
        String message
) {
}
