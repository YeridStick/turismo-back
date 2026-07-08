package co.turismo.api.dto.feedback;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "CreateFeedbackRequest", description = "Detalle del feedback enviado por un usuario autenticado")
public class CreateFeedbackBody {
    @Schema(description = "Tipo de feedback", allowableValues = {"update_info", "suggestion", "complaint", "issue"}, example = "suggestion")
    private String type;
    @Schema(description = "Mensaje descriptivo del feedback", example = "La dirección mostrada ya no es correcta")
    private String message;
    @Schema(description = "Correo para contacto opcional", example = "ana@example.com")
    private String contact_email;
    @Schema(description = "Identificador del dispositivo si no se envía email", example = "android-device-123")
    private String device_id;
}
