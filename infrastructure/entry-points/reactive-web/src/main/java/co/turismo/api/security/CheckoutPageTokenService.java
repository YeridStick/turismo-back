package co.turismo.api.security;

import co.turismo.model.payment.WompiCheckoutData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Component
public class CheckoutPageTokenService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final String signingSecret;

    public CheckoutPageTokenService(@Value("${security.jwt.secret}") String signingSecret) {
        this.signingSecret = signingSecret;
    }

    public String create(WompiCheckoutData checkoutData, String userEmail) {
        long expiresAt = checkoutData.getExpirationTime() == null
                ? Instant.now().plus(10, ChronoUnit.MINUTES).getEpochSecond()
                : checkoutData.getExpirationTime().toInstant().getEpochSecond();
        String payload = String.join("\n",
                checkoutData.getReservationId(),
                userEmail,
                String.valueOf(checkoutData.getTransactionId()),
                String.valueOf(expiresAt));
        String encodedPayload = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public Claims validate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token de checkout requerido");
        }
        int separator = token.lastIndexOf('.');
        if (separator <= 0 || separator == token.length() - 1) {
            throw new IllegalArgumentException("Token de checkout inválido");
        }

        String encodedPayload = token.substring(0, separator);
        String receivedSignature = token.substring(separator + 1);
        String expectedSignature = sign(encodedPayload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Token de checkout inválido");
        }

        String payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        String[] parts = payload.split("\n", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Token de checkout inválido");
        }

        try {
            long expiresAt = Long.parseLong(parts[3]);
            if (Instant.now().getEpochSecond() > expiresAt) {
                throw new IllegalArgumentException("Token de checkout expirado");
            }
            return new Claims(parts[0], parts[1], Long.parseLong(parts[2]), expiresAt);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Token de checkout inválido", error);
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return ENCODER.encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo firmar token de checkout", error);
        }
    }

    public record Claims(String reservationId, String userEmail, Long transactionId, long expiresAt) {}
}
