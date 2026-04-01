package co.turismo.api.dto.auth;
 
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
 
@Schema(name = "RecoveryConfirmRequest", description = "Datos para confirmar recuperación con token")
public record RecoveryConfirmRequest(
        @Schema(example = "ABCDEF")
        @NotBlank(message = "El token es requerido")
        String token,
 
        @Schema(example = "MiNuevaPassword123")
        @NotBlank(message = "La nueva contraseña es requerida")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        String newPassword
) {
}
