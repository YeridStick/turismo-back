package co.turismo.api.dto.reservation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DeleteReservationResponse")
public record DeleteReservationResponse(
        Boolean deleted
) {}
