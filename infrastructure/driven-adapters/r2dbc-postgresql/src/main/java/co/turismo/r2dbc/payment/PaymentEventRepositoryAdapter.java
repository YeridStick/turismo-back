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
                processing_status,
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
                'received',
                NOW()
            )
            ON CONFLICT DO NOTHING
            RETURNING id, provider, event_id, provider_transaction_id, reference, event_type, checksum,
                CAST(payload AS TEXT) AS payload, processed, processing_status, processed_at, processing_error, received_at
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("provider", event.getProvider())
                .bind("payload", event.getPayload());
        spec = bindNullable(spec, "eventId", event.getEventId(), String.class);
        spec = bindNullable(spec, "providerTransactionId", event.getProviderTransactionId(), String.class);
        spec = bindNullable(spec, "reference", event.getReference(), String.class);
        spec = bindNullable(spec, "eventType", event.getEventType(), String.class);
        spec = bindNullable(spec, "checksum", event.getChecksum(), String.class);

        return spec.map((row, metadata) -> toEvent(row)).one()
                .switchIfEmpty(findRetryable(event));
    }

    @Override
    public Mono<Boolean> claimForProcessing(Long id) {
        return db.sql("""
                UPDATE payment_events
                SET processing_status = 'processing'
                WHERE id = :id
                  AND processed = FALSE
                  AND processing_status IN ('received', 'failed')
                """)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Mono<Boolean> markProcessed(Long id) {
        return db.sql("""
                UPDATE payment_events
                SET processed = TRUE,
                    processing_status = 'completed',
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
                    processing_status = 'failed',
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
                .processingStatus(row.get("processing_status", String.class))
                .processedAt(row.get("processed_at", OffsetDateTime.class))
                .processingError(row.get("processing_error", String.class))
                .receivedAt(row.get("received_at", OffsetDateTime.class))
                .build();
    }

    private Mono<PaymentEvent> findRetryable(PaymentEvent event) {
        String sql = """
                SELECT id, provider, event_id, provider_transaction_id, reference, event_type, checksum,
                    CAST(payload AS TEXT) AS payload, processed, processing_status, processed_at, processing_error, received_at
                FROM payment_events
                WHERE provider = :provider
                  AND processed = FALSE
                  AND processing_status IN ('received', 'failed')
                  AND (
                      (:eventId IS NOT NULL AND event_id = :eventId)
                      OR (
                          :eventId IS NULL
                          AND event_id IS NULL
                          AND provider_transaction_id IS NOT DISTINCT FROM :providerTransactionId
                          AND event_type IS NOT DISTINCT FROM :eventType
                          AND checksum IS NOT DISTINCT FROM :checksum
                      )
                  )
                ORDER BY id DESC
                LIMIT 1
                """;
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("provider", event.getProvider());
        spec = bindNullable(spec, "eventId", event.getEventId(), String.class);
        spec = bindNullable(spec, "providerTransactionId", event.getProviderTransactionId(), String.class);
        spec = bindNullable(spec, "eventType", event.getEventType(), String.class);
        spec = bindNullable(spec, "checksum", event.getChecksum(), String.class);
        return spec.map((row, metadata) -> toEvent(row)).one();
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
