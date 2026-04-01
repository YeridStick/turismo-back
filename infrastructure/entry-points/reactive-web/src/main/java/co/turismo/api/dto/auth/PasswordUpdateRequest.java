package co.turismo.api.dto.auth;
 
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
 
@Schema(name = "PasswordUpdateRequest", description = "Solicitud para crear o actualizar contraseña")
public record PasswordUpdateRequest(
        @Schema(example = "MiPasswordSegura123")
        @NotBlank(message = "La contraseña es requerida")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password
) {
}
