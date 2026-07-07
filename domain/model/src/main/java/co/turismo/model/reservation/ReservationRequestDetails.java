package co.turismo.model.reservation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ReservationRequestDetails {
    private Long tourPackageId;
    private String userEmail;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer travelers;
    private String customerPhone;
    private String contactPreference;
    private String customerMessage;
    private Boolean consentAccepted;
    private String consentVersion;
}
