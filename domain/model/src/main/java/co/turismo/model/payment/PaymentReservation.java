package co.turismo.model.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PaymentReservation {
    private String id;
    private String userEmail;
    private Long tourPackageId;
    private String packageTitle;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
}
