package co.turismo.api.dto.reservation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "UpdateReservationStatusRequest")
public record UpdateReservationStatusBody(
        @NotBlank(message = "status es obligatorio")
        @Schema(example = "contacted")
        String status,

        @Schema(example = "Se contactó al cliente por el canal autorizado")
        String notes
) {}
