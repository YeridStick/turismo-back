package co.turismo.r2dbc.payment;

import co.turismo.model.payment.PaymentProvider;
import co.turismo.model.payment.PaymentStatus;
import co.turismo.model.payment.PaymentTransaction;
import co.turismo.model.payment.gateways.PaymentTransactionRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class PaymentTransactionRepositoryAdapter implements PaymentTransactionRepository {

    private final DatabaseClient db;

    @Override
    public Mono<PaymentTransaction> save(PaymentTransaction transaction) {
        String sql = """
            INSERT INTO payment_transactions (
                reservation_id,
                provider,
                reference,
                provider_transaction_id,
                checkout_url,
                amount_in_cents,
                currency,
                status,
                provider_status,
                request_payload,
                response_payload,
                expires_at,
                paid_at,
                created_at,
                updated_at
            )
            VALUES (
                :reservationId,
                :provider,
                :reference,
                :providerTransactionId,
                :checkoutUrl,
                :amountInCents,
                :currency,
                :status,
                :providerStatus,
                CAST(:requestPayload AS JSONB),
                CAST(:responsePayload AS JSONB),
                :expiresAt,
                :paidAt,
                NOW(),
                NOW()
            )
            RETURNING id, reservation_id, provider, reference, provider_transaction_id, checkout_url,
                amount_in_cents, currency, status, provider_status, CAST(request_payload AS TEXT) AS request_payload,
                CAST(response_payload AS TEXT) AS response_payload, expires_at, paid_at, created_at, updated_at
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("reservationId", transaction.getReservationId())
                .bind("provider", transaction.getProvider())
                .bind("reference", transaction.getReference())
                .bind("amountInCents", transaction.getAmountInCents())
                .bind("currency", transaction.getCurrency())
                .bind("status", transaction.getStatus());

        spec = bindNullable(spec, "providerTransactionId", transaction.getProviderTransactionId(), String.class);
        spec = bindNullable(spec, "checkoutUrl", transaction.getCheckoutUrl(), String.class);
        spec = bindNullable(spec, "providerStatus", transaction.getProviderStatus(), String.class);
        spec = bindNullable(spec, "requestPayload", transaction.getRequestPayload(), String.class);
        spec = bindNullable(spec, "responsePayload", transaction.getResponsePayload(), String.class);
        spec = bindNullable(spec, "expiresAt", transaction.getExpiresAt(), OffsetDateTime.class);
        spec = bindNullable(spec, "paidAt", transaction.getPaidAt(), OffsetDateTime.class);

        return spec.map((row, metadata) -> toTransaction(row)).one();
    }

    @Override
    public Mono<PaymentTransaction> findLatestByReservationId(String reservationId) {
        return db.sql(selectSql() + """
                WHERE reservation_id = :reservationId
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """)
                .bind("reservationId", reservationId)
                .map((row, metadata) -> toTransaction(row))
                .one();
    }

    @Override
    public Mono<PaymentTransaction> findReusableWompiTransaction(String reservationId, OffsetDateTime now) {
        return db.sql(selectSql() + """
                WHERE reservation_id = :reservationId
                  AND provider = :provider
                  AND status IN ('checkout_created', 'processing')
                  AND (expires_at IS NULL OR expires_at > :now)
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """)
                .bind("reservationId", reservationId)
                .bind("provider", PaymentProvider.WOMPI)
                .bind("now", now)
                .map((row, metadata) -> toTransaction(row))
                .one();
    }

    @Override
    public Mono<PaymentTransaction> findByReference(String reference) {
        return db.sql(selectSql() + " WHERE reference = :reference")
                .bind("reference", reference)
                .map((row, metadata) -> toTransaction(row))
                .one();
    }

    @Override
    public Mono<Boolean> existsBlockingWompiTransaction(String reservationId, OffsetDateTime now) {
        return db.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM payment_transactions
                    WHERE reservation_id = :reservationId
                      AND provider = :provider
                      AND status IN ('checkout_created', 'processing')
                      AND (expires_at IS NULL OR expires_at > :now)
                ) AS exists
                """)
                .bind("reservationId", reservationId)
                .bind("provider", PaymentProvider.WOMPI)
                .bind("now", now)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("exists", Boolean.class)))
                .one();
    }

    @Override
    public Mono<Boolean> existsPaidWompiTransaction(String reservationId) {
        return db.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM payment_transactions
                    WHERE reservation_id = :reservationId
                      AND provider = :provider
                      AND status = :status
                ) AS exists
                """)
                .bind("reservationId", reservationId)
                .bind("provider", PaymentProvider.WOMPI)
                .bind("status", PaymentStatus.PAID)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("exists", Boolean.class)))
                .one();
    }

    @Override
    public Mono<PaymentTransaction> updateProviderResult(
            String reference,
            String providerTransactionId,
            String providerStatus,
            String status,
            String responsePayload,
            OffsetDateTime paidAt
    ) {
        String sql = """
            UPDATE payment_transactions
            SET provider_transaction_id = COALESCE(:providerTransactionId, provider_transaction_id),
                provider_status = COALESCE(:providerStatus, provider_status),
                status = :status,
                response_payload = COALESCE(CAST(:responsePayload AS JSONB), response_payload),
                paid_at = COALESCE(:paidAt, paid_at),
                updated_at = NOW()
            WHERE reference = :reference
            RETURNING id, reservation_id, provider, reference, provider_transaction_id, checkout_url,
                amount_in_cents, currency, status, provider_status, CAST(request_payload AS TEXT) AS request_payload,
                CAST(response_payload AS TEXT) AS response_payload, expires_at, paid_at, created_at, updated_at
            """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("reference", reference)
                .bind("status", status);
        spec = bindNullable(spec, "providerTransactionId", providerTransactionId, String.class);
        spec = bindNullable(spec, "providerStatus", providerStatus, String.class);
        spec = bindNullable(spec, "responsePayload", responsePayload, String.class);
        spec = bindNullable(spec, "paidAt", paidAt, OffsetDateTime.class);

        return spec.map((row, metadata) -> toTransaction(row)).one();
    }

    private static String selectSql() {
        return """
            SELECT id, reservation_id, provider, reference, provider_transaction_id, checkout_url,
                amount_in_cents, currency, status, provider_status, CAST(request_payload AS TEXT) AS request_payload,
                CAST(response_payload AS TEXT) AS response_payload, expires_at, paid_at, created_at, updated_at
            FROM payment_transactions
            """;
    }

    private static PaymentTransaction toTransaction(Row row) {
        return PaymentTransaction.builder()
                .id(row.get("id", Long.class))
                .reservationId(row.get("reservation_id", String.class))
                .provider(row.get("provider", String.class))
                .reference(row.get("reference", String.class))
                .providerTransactionId(row.get("provider_transaction_id", String.class))
                .checkoutUrl(row.get("checkout_url", String.class))
                .amountInCents(row.get("amount_in_cents", Long.class))
                .currency(row.get("currency", String.class))
                .status(row.get("status", String.class))
                .providerStatus(row.get("provider_status", String.class))
                .requestPayload(row.get("request_payload", String.class))
                .responsePayload(row.get("response_payload", String.class))
                .expiresAt(row.get("expires_at", OffsetDateTime.class))
                .paidAt(row.get("paid_at", OffsetDateTime.class))
                .createdAt(row.get("created_at", OffsetDateTime.class))
                .updatedAt(row.get("updated_at", OffsetDateTime.class))
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
