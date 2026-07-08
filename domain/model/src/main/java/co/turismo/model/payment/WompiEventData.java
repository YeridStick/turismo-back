package co.turismo.model.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class WompiEventData {
    private String eventId;
    private String eventType;
    private String providerTransactionId;
    private String reference;
    private String providerStatus;
    private Long amountInCents;
    private String currency;
    private String checksum;
    private String headerChecksum;
    private String rawPayload;
    private boolean signatureValid;
}
