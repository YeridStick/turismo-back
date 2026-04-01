package co.turismo.api.dto.auth;
 
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
 
@Schema(name = "RecoveryRequest", description = "Solicitud de enlace de recuperación")
public record RecoveryRequest(
        @Schema(example = "ana@example.com")
        @NotBlank(message = "El email es requerido")
        @Email(message = "Email con formato inválido")
        String email
) {
}
