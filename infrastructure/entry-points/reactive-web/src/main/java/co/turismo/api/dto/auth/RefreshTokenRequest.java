package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RefreshTokenRequest", description = "Token que se desea refrescar cuando no viene por header Authorization")
public record RefreshTokenRequest(
        @Schema(description = "JWT actualmente vigente", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token
) {
}
