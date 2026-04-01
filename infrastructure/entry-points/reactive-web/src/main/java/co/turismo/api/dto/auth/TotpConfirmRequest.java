package co.turismo.api.dto.auth;
 
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
 
@Schema(name = "TotpConfirmRequest", description = "Confirmación de código OTP")
public record TotpConfirmRequest(
        @Schema(example = "ana@example.com")
        @NotBlank(message = "El email es requerido")
        String email,
 
        @Schema(example = "123456")
        @NotBlank(message = "El código es requerido")
        String code
) {
}
