package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.visit.CheckinResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiCheckinResponse", description = "Respuesta al realizar un check-in en un lugar")
public record ApiCheckinResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        CheckinResponse data
) {
}
