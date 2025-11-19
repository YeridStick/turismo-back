package co.turismo.api.dto.response.docs;

import co.turismo.model.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiUserResponse", description = "Envelope con la información pública del usuario")
public record ApiUserResponse(
        @Schema(example = "200")
        int status,
        @Schema(example = "OK")
        String message,
        User data
) {
}
