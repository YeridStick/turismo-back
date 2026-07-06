package co.turismo.model.notification;

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
public class AppNotification {
    private Long id;
    private String recipientEmail;
    private String type;
    private String title;
    private String message;
    private String reservationId;
    private Long agencyId;
    private Boolean read;
    private OffsetDateTime createdAt;
}
