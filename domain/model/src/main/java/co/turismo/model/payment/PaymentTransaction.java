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
public class PaymentTransaction {
    private Long id;
    private String reservationId;
    private String provider;
    private String reference;
    private String providerTransactionId;
    private String checkoutUrl;
    private Long amountInCents;
    private String currency;
    private String status;
    private String providerStatus;
    private String requestPayload;
    private String responsePayload;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
