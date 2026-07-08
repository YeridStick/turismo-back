package co.turismo.api.dto.agency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "AddAgencyUserRequest", description = "Asociar usuario a la agencia del solicitante")
public record AddAgencyUserBody(
        @Schema(description = "Email de la agencia", example = "nuevo@correo.com")
        @NotBlank String emailAgencia,
        @Schema(description = "Email del usuario a asociar", example = "nuevo@correo.com")
        @NotBlank String email
) {
}

