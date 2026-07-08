package co.turismo.api.dto.reservation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "CreateReservationRequest")
public record CreateReservationBody(
        @NotNull(message = "tourPackageId es obligatorio")
        @Schema(example = "6")
        Long tourPackageId,

        @NotNull(message = "startDate es obligatorio")
        @FutureOrPresent(message = "startDate no puede estar en el pasado")
        @Schema(example = "2026-07-20")
        LocalDate startDate,

        @Schema(example = "2026-07-22")
        LocalDate endDate,

        @NotNull(message = "travelers es obligatorio")
        @Min(value = 1, message = "travelers debe ser mayor que cero")
        @Schema(example = "2")
        Integer travelers,

        @Schema(example = "3000000000", description = "Opcional y no verificado. Los canales confiables son EMAIL e IN_APP.")
        String customerPhone,

        @Schema(example = "EMAIL", description = "Canal confiable: EMAIL o IN_APP.")
        String contactPreference,

        @Schema(example = "Deseo confirmar si el paquete incluye transporte")
        String message,

        @NotNull(message = "consentAccepted es obligatorio")
        @AssertTrue(message = "consentAccepted debe ser true")
        @Schema(example = "true")
        Boolean consentAccepted,

        @NotBlank(message = "consentVersion es obligatorio")
        @Schema(example = "2026-07-04")
        String consentVersion
) {}
