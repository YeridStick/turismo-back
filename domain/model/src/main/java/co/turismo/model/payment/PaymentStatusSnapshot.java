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
public class PaymentStatusSnapshot {
    private String reservationId;
    private String reservationStatus;
    private String paymentProvider;
    private String paymentStatus;
    private String providerTransactionId;
    private String reference;
    private Long amountInCents;
    private String currency;
    private OffsetDateTime paidAt;
    private OffsetDateTime expiresAt;
}
