package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RegisterUserRequest", description = "Datos requeridos para registrar un usuario")
public record RegisterUserRequest(
        @Schema(example = "Ana Perez")
        String full_name,
        @Schema(example = "ana@example.com")
        String email,
        @Schema(example = "https://cdn.example.com/avatar.png")
        String url_avatar,
        @Schema(example = "CC")
        String identification_type,
        @Schema(example = "1234567890")
        String identification_number,
        @Schema(example = "MiPasswordSegura123")
        String password
) {
}
