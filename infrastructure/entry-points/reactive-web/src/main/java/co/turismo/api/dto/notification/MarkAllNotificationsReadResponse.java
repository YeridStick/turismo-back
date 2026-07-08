package co.turismo.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MarkAllNotificationsReadResponse")
public record MarkAllNotificationsReadResponse(
        Long updated
) {}
