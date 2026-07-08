package co.turismo.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "NotificationResponse")
public record NotificationResponse(
        Long id,
        String recipientEmail,
        String type,
        String title,
        String message,
        String reservationId,
        Long agencyId,
        Boolean read,
        OffsetDateTime createdAt
) {}
