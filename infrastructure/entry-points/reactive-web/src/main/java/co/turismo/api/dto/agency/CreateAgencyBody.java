package co.turismo.api.dto.agency;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "CreateAgencyRequest", description = "Cuerpo para crear una agencia")
public record CreateAgencyBody(
        @Schema(description = "Nombre de la agencia", example = "Turismo Huila")
        @NotBlank String name,
        @Schema(description = "Descripción corta")
        String description,
        @Schema(description = "Teléfono de contacto")
        String phone,
        @Schema(description = "Email de la agencia")
        String email,
        @Schema(description = "Sitio web")
        String website,
        @Schema(description = "Logo URL")
        String logoUrl
) {
}