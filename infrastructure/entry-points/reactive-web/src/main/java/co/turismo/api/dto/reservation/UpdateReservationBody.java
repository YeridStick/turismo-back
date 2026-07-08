package co.turismo.api.dto.reservation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(name = "UpdateReservationRequest")
public record UpdateReservationBody(
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

        @Schema(example = "3000000000")
        String customerPhone,

        @Schema(example = "EMAIL")
        String contactPreference,

        @Schema(example = "Deseo ajustar la fecha")
        String message
) {}
