package co.turismo.model.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class WompiCheckoutData {
    private String reservationId;
    private Long transactionId;
    private String provider;
    private String reference;
    private Long amountInCents;
    private String currency;
    private String publicKey;
    private String signatureIntegrity;
    private String redirectUrl;
    private OffsetDateTime expirationTime;
    private String customerEmail;
    private String checkoutUrl;
    private String status;
}
