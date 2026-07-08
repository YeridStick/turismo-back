package co.turismo.api.dto.reservationmessage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "SendReservationMessageRequest")
public record SendReservationMessageBody(
        @NotBlank(message = "message es obligatorio")
        @Size(max = 2000, message = "message excede la longitud permitida")
        @Schema(example = "Hola, quiero confirmar el proceso de pago.")
        String message
) {}
