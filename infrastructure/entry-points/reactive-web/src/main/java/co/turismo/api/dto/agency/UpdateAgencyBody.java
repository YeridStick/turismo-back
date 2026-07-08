package co.turismo.api.dto.agency;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateAgencyRequest", description = "Cuerpo para actualizar una agencia")
public record UpdateAgencyBody(
        @Schema(description = "Nombre de la agencia", example = "Turismo Huila")
        String name,
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
) {}
