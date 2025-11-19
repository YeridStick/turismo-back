package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.common.SimpleMessageResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiMessageResponse", description = "Envelope estándar con un mensaje descriptivo en data")
public record ApiMessageResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        SimpleMessageResponse data
) {
}
