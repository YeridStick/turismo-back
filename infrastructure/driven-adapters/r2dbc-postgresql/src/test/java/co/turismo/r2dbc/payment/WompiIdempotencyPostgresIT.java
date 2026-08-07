package co.turismo.r2dbc.payment;

import co.turismo.model.payment.PaymentEvent;
import co.turismo.model.reservation.ReservationMessage;
import co.turismo.r2dbc.reservation.ReservationMessageRepositoryAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;
import static io.r2dbc.spi.ConnectionFactoryOptions.builder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_INTEGRATION", matches = "true")
class WompiIdempotencyPostgresIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withEnv("POSTGRES_HOST_AUTH_METHOD", "trust");

    private static DatabaseClient db;
    private static PaymentEventRepositoryAdapter events;
    private static ReservationMessageRepositoryAdapter messages;

    @BeforeAll
    static void setUp() {
        var options = builder()
                .option(DRIVER, "postgresql")
                .option(HOST, POSTGRES.getHost())
                .option(PORT, POSTGRES.getFirstMappedPort())
                .option(USER, POSTGRES.getUsername())
                .option(PASSWORD, POSTGRES.getPassword())
                .option(DATABASE, POSTGRES.getDatabaseName())
                .build();
        db = DatabaseClient.builder()
                .connectionFactory(io.r2dbc.spi.ConnectionFactories.get(options))
                .build();
        execute("""
                CREATE TABLE payment_events (
                    id BIGSERIAL PRIMARY KEY,
                    provider VARCHAR(30) NOT NULL,
                    event_id VARCHAR(160),
                    provider_transaction_id VARCHAR(120),
                    reference VARCHAR(120),
                    event_type VARCHAR(80),
                    checksum VARCHAR(160),
                    payload JSONB NOT NULL,
                    processed BOOLEAN NOT NULL DEFAULT FALSE,
                    processed_at TIMESTAMPTZ,
                    processing_error TEXT,
                    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    UNIQUE (provider, event_id)
                )
                """);
        execute("""
                CREATE TABLE reservation_messages (
                    id BIGSERIAL PRIMARY KEY,
                    reservation_id VARCHAR(80) NOT NULL,
                    sender_email VARCHAR(255) NOT NULL,
                    sender_type VARCHAR(20) NOT NULL,
                    body TEXT NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """);
        applyMigration();
        events = new PaymentEventRepositoryAdapter(db);
        messages = new ReservationMessageRepositoryAdapter(db);
    }

    @Test
    void appliesV9AndMakesEventAndSuccessMessageIdempotent() {
        assertTrue(exists("SELECT 1 FROM information_schema.columns WHERE table_name = 'payment_events' AND column_name = 'processing_status'"));
        assertTrue(exists("SELECT 1 FROM pg_constraint WHERE conname = 'payment_events_processing_status_check'"));
        assertTrue(exists("SELECT 1 FROM pg_indexes WHERE indexname = 'uq_reservation_messages_system_event'"));

        PaymentEvent event = PaymentEvent.builder()
                .provider("wompi")
                .eventId("evt-integration-1")
                .providerTransactionId("tx-integration-1")
                .reference("reservation-integration-1")
                .eventType("transaction.updated")
                .checksum("checksum-1")
                .payload("{\"status\":\"APPROVED\"}")
                .build();

        PaymentEvent received = events.saveIfAbsent(event).block(Duration.ofSeconds(10));
        assertNotNull(received);
        assertEquals("received", status(received.getId()));

        List<Boolean> claims = Mono.zip(
                        events.claimForProcessing(received.getId()),
                        events.claimForProcessing(received.getId()))
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                .block(Duration.ofSeconds(10));
        assertNotNull(claims);
        assertEquals(1, claims.stream().filter(Boolean.TRUE::equals).count());
        assertEquals("processing", status(received.getId()));

        assertTrue(events.markFailed(received.getId(), "temporary failure").block(Duration.ofSeconds(10)));
        assertEquals("failed", status(received.getId()));
        PaymentEvent retry = events.saveIfAbsent(event).block(Duration.ofSeconds(10));
        assertNotNull(retry);
        assertTrue(events.claimForProcessing(retry.getId()).block(Duration.ofSeconds(10)));
        assertTrue(events.markProcessed(retry.getId()).block(Duration.ofSeconds(10)));
        assertEquals("completed", status(received.getId()));
        assertTrue(events.saveIfAbsent(event).blockOptional(Duration.ofSeconds(10)).isEmpty());

        ReservationMessage message = ReservationMessage.builder()
                .reservationId("reservation-integration-1")
                .senderEmail("system@turismo.test")
                .senderType("SYSTEM")
                .systemEventKey("WOMPI_PAYMENT_SUCCESS")
                .body("Pago recibido")
                .build();
        messages.save(message).block(Duration.ofSeconds(10));
        messages.save(message.toBuilder().body("Pago recibido; reserva confirmada").build())
                .block(Duration.ofSeconds(10));

        Long count = db.sql("SELECT COUNT(*) FROM reservation_messages WHERE reservation_id = :id AND system_event_key = :key")
                .bind("id", message.getReservationId())
                .bind("key", message.getSystemEventKey())
                .map((row, metadata) -> row.get(0, Long.class))
                .one()
                .block(Duration.ofSeconds(10));
        assertEquals(1L, count);
        assertEquals("Pago recibido; reserva confirmada", messages.findByReservationId(
                        message.getReservationId(), 10, 0).collectList().block(Duration.ofSeconds(10)).get(0).getBody());
        assertFalse(Boolean.TRUE.equals(received.getProcessed()));
    }

    private static String status(Long id) {
        return db.sql("SELECT processing_status FROM payment_events WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> row.get(0, String.class))
                .one()
                .block(Duration.ofSeconds(10));
    }

    private static boolean exists(String sql) {
        return db.sql(sql)
                .map((row, metadata) -> true)
                .one()
                .defaultIfEmpty(false)
                .block(Duration.ofSeconds(10));
    }

    private static void applyMigration() {
        try {
            Path migration = Path.of("").toAbsolutePath();
            while (migration != null && !Files.exists(migration.resolve("deployment/V9__wompi_idempotency.sql"))) {
                migration = migration.getParent();
            }
            if (migration == null) {
                throw new java.nio.file.NoSuchFileException("deployment/V9__wompi_idempotency.sql");
            }
            migration = migration.resolve("deployment/V9__wompi_idempotency.sql");
            for (String statement : Files.readString(migration).split(";")) {
                if (!statement.isBlank()) execute(statement);
            }
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo aplicar V9__wompi_idempotency.sql", error);
        }
    }

    private static void execute(String sql) {
        db.sql(sql).fetch().rowsUpdated().onErrorResume(error -> Mono.error(error)).block(Duration.ofSeconds(10));
    }
}
