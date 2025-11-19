package co.turismo.api.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "JwtTokenResponse", description = "Contenedor para el token JWT emitido por la API")
public record JwtTokenResponse(
        @Schema(description = "JWT firmado para acceder a los recursos protegidos", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String token
) {
}
