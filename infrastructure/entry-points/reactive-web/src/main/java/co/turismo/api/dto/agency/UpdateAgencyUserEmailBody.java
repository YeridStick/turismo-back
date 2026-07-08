package co.turismo.api.dto.agency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "UpdateAgencyUserEmailRequest", description = "Corregir el correo de un usuario vinculado")
public record UpdateAgencyUserEmailBody(
        @Schema(description = "Nuevo email", example = "corregido@correo.com")
        @NotBlank String email
) {
}