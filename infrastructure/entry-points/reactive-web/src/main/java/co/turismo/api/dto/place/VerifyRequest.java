package co.turismo.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "VerifyPlaceRequest", description = "Aprobación o rechazo desde el panel de administración")
public record VerifyRequest(
        @Schema(description = "true para aprobar, false para rechazar", example = "true")
        boolean approve
) {
}
