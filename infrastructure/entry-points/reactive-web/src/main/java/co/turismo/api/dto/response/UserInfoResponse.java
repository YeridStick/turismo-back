package co.turismo.api.dto.response;

import co.turismo.model.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserInfoResponse", description = "Información del usuario con estado de verificación y contraseña")
public record UserInfoResponse(
        User user,
        @Schema(example = "true")
        boolean emailVerified,
        @Schema(example = "false")
        boolean passwordEnabled
) {
}
