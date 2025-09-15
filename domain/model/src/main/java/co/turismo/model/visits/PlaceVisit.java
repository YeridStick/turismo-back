package co.turismo.model.visits;
import lombok.*;

import java.time.Instant;


@Getter
@Setter
@AllArgsConstructor
@Builder(toBuilder = true)
@Value
public class PlaceVisit {
    Long id;
    Long placeId;
    Long userId;        // puede ser null para anónimo
    String deviceId;
    Instant startedAt;
    Instant confirmedAt;
    VisitStatus status;
    Integer distanceM;
    Integer accuracyM;
    String metaJson;    // JSON en texto (ligero y agnóstico)
}