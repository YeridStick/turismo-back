package co.turismo.model.notification.gateways;

import co.turismo.model.notification.AppNotification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AppNotificationGateway {
    Mono<AppNotification> save(AppNotification notification);
    Flux<AppNotification> findByRecipientEmail(String recipientEmail, boolean unreadOnly, int limit, int offset);
    Flux<AppNotification> streamByRecipientEmail(String recipientEmail);
    Mono<AppNotification> markAsRead(String recipientEmail, Long notificationId);
    Mono<Long> markAllAsRead(String recipientEmail);
}
