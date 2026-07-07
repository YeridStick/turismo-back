package co.turismo.model.reservation;

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
public class ReservationMessage {
    private Long id;
    private String reservationId;
    private String senderEmail;
    private String senderType;
    private String body;
    private OffsetDateTime createdAt;
}
