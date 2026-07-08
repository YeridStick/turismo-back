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
public class PaymentEvent {
    private Long id;
    private String provider;
    private String eventId;
    private String providerTransactionId;
    private String reference;
    private String eventType;
    private String checksum;
    private String payload;
    private Boolean processed;
    private OffsetDateTime processedAt;
    private String processingError;
    private OffsetDateTime receivedAt;
}
