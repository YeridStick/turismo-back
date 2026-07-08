package co.turismo.r2dbc.payment;

import co.turismo.model.payment.PaymentEvent;
import co.turismo.model.payment.gateways.PaymentEventRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class PaymentEventRepositoryAdapter implements PaymentEventRepository {

    private final DatabaseClient db;

    @Override
    public Mono<PaymentEvent> saveIfAbsent(PaymentEvent event) {
        String sql = """
            INSERT INTO payment_events (
                provider,
                event_id,
                provider_transaction_id,
                reference,
                event_type,
                checksum,
                payload,
                processed,
                received_at
            )
            VALUES (
                :provider,
                :eventId,
                :providerTransactionId,
                :reference,
                :eventType,
                :checksum,
                CAST(:payload AS JSONB),
                FALSE,
                NOW()
            )
            ON CONFLICT DO NOTHING
            RETURNING id, provider, event_id, provider_transaction_id, reference, event_type, checksum,
                CAST(payload AS TEXT) AS payload, processed, processed_at, processing_error, received_at
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("provider", event.getProvider())
                .bind("payload", event.getPayload());
        spec = bindNullable(spec, "eventId", event.getEventId(), String.class);
        spec = bindNullable(spec, "providerTransactionId", event.getProviderTransactionId(), String.class);
        spec = bindNullable(spec, "reference", event.getReference(), String.class);
        spec = bindNullable(spec, "eventType", event.getEventType(), String.class);
        spec = bindNullable(spec, "checksum", event.getChecksum(), String.class);

        return spec.map((row, metadata) -> toEvent(row)).one();
    }

    @Override
    public Mono<Boolean> markProcessed(Long id) {
        return db.sql("""
                UPDATE payment_events
                SET processed = TRUE,
                    processed_at = NOW(),
                    processing_error = NULL
                WHERE id = :id
                """)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Boolean> markFailed(Long id, String error) {
        return db.sql("""
                UPDATE payment_events
                SET processed = FALSE,
                    processing_error = :error
                WHERE id = :id
                """)
                .bind("id", id)
                .bind("error", error == null ? "Error procesando webhook" : error)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    private static PaymentEvent toEvent(Row row) {
        return PaymentEvent.builder()
                .id(row.get("id", Long.class))
                .provider(row.get("provider", String.class))
                .eventId(row.get("event_id", String.class))
                .providerTransactionId(row.get("provider_transaction_id", String.class))
                .reference(row.get("reference", String.class))
                .eventType(row.get("event_type", String.class))
                .checksum(row.get("checksum", String.class))
                .payload(row.get("payload", String.class))
                .processed(row.get("processed", Boolean.class))
                .processedAt(row.get("processed_at", OffsetDateTime.class))
                .processingError(row.get("processing_error", String.class))
                .receivedAt(row.get("received_at", OffsetDateTime.class))
                .build();
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec,
            String name,
            T value,
            Class<?> type
    ) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
