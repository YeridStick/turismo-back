package co.turismo.api.dto.auth;
 
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
 
@Schema(name = "TotpEmailRequest", description = "Solicitud de setup TOTP")
public record TotpEmailRequest(
        @Schema(example = "ana@example.com")
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email con formato inválido")
        String email,
 
        @Schema(example = "Password123")
        @NotBlank(message = "La contraseña es requerida")
        String password
) {
}
