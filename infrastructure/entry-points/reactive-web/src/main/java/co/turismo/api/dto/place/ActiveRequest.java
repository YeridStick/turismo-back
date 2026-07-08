package co.turismo.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SetPlaceActiveRequest", description = "Activa o desactiva un lugar publicado")
public record ActiveRequest(
        @Schema(description = "true para activar, false para suspender", example = "false")
        boolean active
) {
}
