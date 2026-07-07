package co.turismo.usecase.notification;

import co.turismo.model.error.NotFoundException;
import co.turismo.model.notification.AppNotification;
import co.turismo.model.notification.gateways.AppNotificationGateway;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class AppNotificationUseCase {

    private final AppNotificationGateway appNotificationGateway;

    public Flux<AppNotification> findMine(String recipientEmail, boolean unreadOnly, int limit, int offset) {
        return appNotificationGateway.findByRecipientEmail(
                requireText(recipientEmail, "Usuario autenticado requerido"),
                unreadOnly,
                limit,
                offset);
    }

    public Flux<AppNotification> streamMine(String recipientEmail) {
        return appNotificationGateway.streamByRecipientEmail(requireText(recipientEmail, "Usuario autenticado requerido"));
    }

    public Mono<AppNotification> markAsRead(String recipientEmail, Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            return Mono.error(new IllegalArgumentException("notificationId inválido"));
        }
        return appNotificationGateway.markAsRead(
                        requireText(recipientEmail, "Usuario autenticado requerido"),
                        notificationId)
                .switchIfEmpty(Mono.error(new NotFoundException("Notificación no encontrada")));
    }

    public Mono<Long> markAllAsRead(String recipientEmail) {
        return appNotificationGateway.markAllAsRead(requireText(recipientEmail, "Usuario autenticado requerido"));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
