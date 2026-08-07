package co.turismo.r2dbc.reservation;

import co.turismo.model.reservation.ReservationMessage;
import co.turismo.model.reservation.gateways.ReservationMessageGateway;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class ReservationMessageRepositoryAdapter implements ReservationMessageGateway {

    private final DatabaseClient db;

    @Override
    public Mono<ReservationMessage> save(ReservationMessage message) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                    INSERT INTO reservation_messages (
                        reservation_id,
                        sender_email,
                        sender_type,
                        system_event_key,
                        body,
                        created_at
                    )
                    VALUES (
                        :reservationId,
                        :senderEmail,
                        :senderType,
                        :systemEventKey,
                        :body,
                        NOW()
                    )
                    ON CONFLICT (reservation_id, system_event_key) WHERE system_event_key IS NOT NULL
                    DO UPDATE SET body = EXCLUDED.body
                    RETURNING id, reservation_id, sender_email, sender_type, system_event_key, body, created_at
                """)
                .bind("reservationId", message.getReservationId())
                .bind("senderEmail", message.getSenderEmail())
                .bind("senderType", message.getSenderType());
        spec = message.getSystemEventKey() == null
                ? spec.bindNull("systemEventKey", String.class)
                : spec.bind("systemEventKey", message.getSystemEventKey());
        return spec.bind("body", message.getBody())
                .map((row, metadata) -> toMessage(row))
                .one();
    }

    @Override
    public Flux<ReservationMessage> findByReservationId(String reservationId, int limit, int offset) {
        return db.sql("""
                    SELECT id, reservation_id, sender_email, sender_type, system_event_key, body, created_at
                    FROM reservation_messages
                    WHERE reservation_id = :reservationId
                    ORDER BY created_at ASC, id ASC
                    LIMIT :limit OFFSET :offset
                """)
                .bind("reservationId", reservationId)
                .bind("limit", limit)
                .bind("offset", offset)
                .map((row, metadata) -> toMessage(row))
                .all();
    }

    private ReservationMessage toMessage(Row row) {
        return ReservationMessage.builder()
                .id(row.get("id", Long.class))
                .reservationId(row.get("reservation_id", String.class))
                .senderEmail(row.get("sender_email", String.class))
                .senderType(row.get("sender_type", String.class))
                .systemEventKey(row.get("system_event_key", String.class))
                .body(row.get("body", String.class))
                .createdAt(row.get("created_at", OffsetDateTime.class))
                .build();
    }
}
