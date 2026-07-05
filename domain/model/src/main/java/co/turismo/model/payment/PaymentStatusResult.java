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
public class PaymentStatusResult {
    private String paymentId;
    private String status;
    private String statusDetail;
    private String externalReference;

    public boolean isApproved() {
        return "approved".equalsIgnoreCase(status) || "paid".equalsIgnoreCase(status);
    }
}
