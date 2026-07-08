package co.turismo.api.dto.reservationmessage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "ReservationMessageResponse")
public record ReservationMessageResponse(
        Long id,
        String reservationId,
        String senderEmail,
        String senderType,
        String message,
        OffsetDateTime createdAt
) {}
