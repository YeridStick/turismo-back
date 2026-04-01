package co.turismo.api.dto.auth;
 
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
 
@Schema(name = "EmailVerificationRequest", description = "Solicitud para verificar un correo")
public record EmailVerificationRequest(
        @Schema(example = "ana@example.com")
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email con formato inválido")
        String email
) {
}
