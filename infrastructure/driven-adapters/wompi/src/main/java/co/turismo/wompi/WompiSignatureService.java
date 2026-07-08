package co.turismo.wompi;

import co.turismo.model.payment.PaymentTransaction;
import co.turismo.model.payment.WompiEventData;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Component
public class WompiSignatureService {

    private static final DateTimeFormatter WOMPI_EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public String checkoutIntegrity(PaymentTransaction transaction, String integritySecret) {
        String expiration = transaction.getExpiresAt() == null
                ? ""
                : formatExpirationTime(transaction.getExpiresAt());
        return sha256(transaction.getReference()
                + transaction.getAmountInCents()
                + transaction.getCurrency()
                + expiration
                + safe(integritySecret));
    }

    public String formatExpirationTime(OffsetDateTime expirationTime) {
        return WOMPI_EXPIRATION_FORMATTER.format(
                expirationTime.toInstant().truncatedTo(ChronoUnit.MILLIS));
    }

    public boolean isValidEvent(JsonNode root, WompiEventData event, String eventsSecret) {
        if (eventsSecret == null || eventsSecret.isBlank()) {
            return false;
        }

        String sentChecksum = hasText(event.getHeaderChecksum()) ? event.getHeaderChecksum() : event.getChecksum();
        if (sentChecksum == null || sentChecksum.isBlank()) {
            return false;
        }

        String timestamp = textAt(root, "/timestamp");
        if (timestamp == null) {
            timestamp = textAt(root, "/sent_at");
        }

        String expected = sha256(
                safe(event.getProviderTransactionId())
                        + safe(event.getProviderStatus())
                        + safe(event.getAmountInCents())
                        + safe(timestamp)
                        + eventsSecret
        );

        if (constantTimeEquals(expected, sentChecksum)) {
            return true;
        }

        String fallback = sha256(
                safe(event.getProviderTransactionId())
                        + safe(event.getProviderStatus())
                        + safe(event.getAmountInCents())
                        + eventsSecret
        );
        return constantTimeEquals(fallback, sentChecksum);
    }

    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo calcular firma SHA-256", error);
        }
    }

    private static String textAt(JsonNode root, String pointer) {
        JsonNode node = root.at(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean constantTimeEquals(String expected, String received) {
        if (expected == null || received == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}
