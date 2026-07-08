package co.turismo.api.mapper;

import co.turismo.api.dto.notification.MarkAllNotificationsReadResponse;
import co.turismo.api.dto.notification.NotificationResponse;
import co.turismo.model.notification.AppNotification;

public final class AppNotificationMapper {

    private AppNotificationMapper() {}

    public static NotificationResponse toNotificationResponse(AppNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getRecipientEmail(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getReservationId(),
                notification.getAgencyId(),
                notification.getRead(),
                notification.getCreatedAt()
        );
    }

    public static MarkAllNotificationsReadResponse toMarkAllNotificationsReadResponse(Long updated) {
        return new MarkAllNotificationsReadResponse(updated);
    }
}