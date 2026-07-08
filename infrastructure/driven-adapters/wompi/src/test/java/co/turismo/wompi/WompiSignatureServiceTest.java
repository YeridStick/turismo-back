package co.turismo.wompi;

import co.turismo.model.payment.PaymentTransaction;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WompiSignatureServiceTest {

    private final WompiSignatureService service = new WompiSignatureService();

    @Test
    void sha256ShouldReturnExpectedHexValue() {
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                service.sha256("hello"));
    }

    @Test
    void checkoutIntegrityShouldUseReferenceAmountCurrencyExpirationAndSecret() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-07-20T10:15:30-05:00");
        PaymentTransaction transaction = PaymentTransaction.builder()
                .reference("ref-1")
                .amountInCents(1000000L)
                .currency("COP")
                .expiresAt(expiresAt)
                .build();

        String expected = service.sha256("ref-11000000COP2026-07-20T15:15:30.000Zsecret");

        assertEquals(expected, service.checkoutIntegrity(transaction, "secret"));
    }
}
