package co.turismo.r2dbc.visitsRepository.entity;

import io.r2dbc.postgresql.codec.Json;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("place_visits")
public class PlaceVisitData {
    @Id
    private Long id;

    @Column("place_id")
    private Long placeId;
    @Column("user_id")
    private Long userId;
    @Column("device_id")
    private String deviceId;
    @Column("started_at")
    private Instant startedAt;
    @Column("confirmed_at")
    private Instant confirmedAt;
    @Column("status")
    private String status;
    @Column("distance_m")
    private Integer distanceM;
    @Column("accuracy_m")
    private Integer accuracyM;
    @Column("meta")
    private Json meta;
}