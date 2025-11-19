package co.turismo.api.dto.response.docs;

import co.turismo.api.dto.auth.JwtTokenResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiTokenResponse", description = "Respuesta con un JWT válido en la propiedad data")
public record ApiTokenResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        JwtTokenResponse data
) {
}
