package co.turismo.r2dbc.notification;

import co.turismo.model.notification.AppNotification;
import co.turismo.model.notification.gateways.AppNotificationGateway;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@RequiredArgsConstructor
public class AppNotificationRepositoryAdapter implements AppNotificationGateway {

    private final DatabaseClient db;
    private final Map<String, Sinks.Many<AppNotification>> streams = new ConcurrentHashMap<>();

    @Override
    public Mono<AppNotification> save(AppNotification notification) {
        return db.sql("""
                    INSERT INTO notifications (
                        recipient_email,
                        type,
                        title,
                        message,
                        reservation_id,
                        agency_id,
                        read,
                        created_at
                    )
                    VALUES (
                        :recipientEmail,
                        :type,
                        :title,
                        :message,
                        :reservationId,
                        :agencyId,
                        COALESCE(:read, FALSE),
                        NOW()
                    )
                    RETURNING id, recipient_email, type, title, message, reservation_id, agency_id, read, created_at
                """)
                .bind("recipientEmail", notification.getRecipientEmail())
                .bind("type", notification.getType())
                .bind("title", notification.getTitle())
                .bind("message", notification.getMessage())
                .bind("read", Boolean.TRUE.equals(notification.getRead()))
                .bind("reservationId", notification.getReservationId())
                .bind("agencyId", notification.getAgencyId())
                .map((row, metadata) -> toNotification(row))
                .one()
                .doOnNext(this::emit);
    }

    @Override
    public Flux<AppNotification> findByRecipientEmail(String recipientEmail, boolean unreadOnly, int limit, int offset) {
        String sql = """
                    SELECT id, recipient_email, type, title, message, reservation_id, agency_id, read, created_at
                    FROM notifications
                    WHERE recipient_email = :recipientEmail
                      AND (:unreadOnly = FALSE OR read = FALSE)
                    ORDER BY created_at DESC, id DESC
                    LIMIT :limit OFFSET :offset
                """;

        return db.sql(sql)
                .bind("recipientEmail", recipientEmail)
                .bind("unreadOnly", unreadOnly)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> toNotification(row))
                .all();
    }

    @Override
    public Flux<AppNotification> streamByRecipientEmail(String recipientEmail) {
        return sinkFor(recipientEmail).asFlux();
    }

    @Override
    public Mono<AppNotification> markAsRead(String recipientEmail, Long notificationId) {
        return db.sql("""
                    UPDATE notifications
                    SET read = TRUE
                    WHERE id = :notificationId
                      AND recipient_email = :recipientEmail
                    RETURNING id, recipient_email, type, title, message, reservation_id, agency_id, read, created_at
                """)
                .bind("notificationId", notificationId)
                .bind("recipientEmail", recipientEmail)
                .map((row, metadata) -> toNotification(row))
                .one();
    }

    @Override
    public Mono<Long> markAllAsRead(String recipientEmail) {
        return db.sql("""
                    UPDATE notifications
                    SET read = TRUE
                    WHERE recipient_email = :recipientEmail
                      AND read = FALSE
                """)
                .bind("recipientEmail", recipientEmail)
                .fetch()
                .rowsUpdated();
    }

    private void emit(AppNotification notification) {
        sinkFor(notification.getRecipientEmail()).tryEmitNext(notification);
    }

    private Sinks.Many<AppNotification> sinkFor(String recipientEmail) {
        return streams.computeIfAbsent(
                recipientEmail,
                key -> Sinks.many().multicast().directBestEffort());
    }

    private AppNotification toNotification(Row row) {
        return AppNotification.builder()
                .id(row.get("id", Long.class))
                .recipientEmail(row.get("recipient_email", String.class))
                .type(row.get("type", String.class))
                .title(row.get("title", String.class))
                .message(row.get("message", String.class))
                .reservationId(row.get("reservation_id", String.class))
                .agencyId(row.get("agency_id", Long.class))
                .read(row.get("read", Boolean.class))
                .createdAt(row.get("created_at", OffsetDateTime.class))
                .build();
    }
}
