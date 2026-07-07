package co.turismo.model.reservation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ReservationDraft {
    private String id;
    private String userEmail;
    private Long tourPackageId;
    private Long agencyId;
    private String packageTitle;
    private BigDecimal totalAmount;
    private String currency;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer travelers;
    private String customerPhone;
    private String contactPreference;
    private String customerMessage;
    private Boolean consentAccepted;
    private String consentVersion;
    private OffsetDateTime consentAcceptedAt;
    private String status;
    private String paymentProvider;
    private String paymentStatus;
    private String paymentId;
    private OffsetDateTime paidAt;
    private String agencyNotes;
    private OffsetDateTime contactedAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime cancelledAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
