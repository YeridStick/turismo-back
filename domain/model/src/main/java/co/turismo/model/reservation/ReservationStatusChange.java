package co.turismo.model.reservation;

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
public class ReservationStatusChange {
    private String reservationId;
    private String agencyUserEmail;
    private String status;
    private String notes;
}
