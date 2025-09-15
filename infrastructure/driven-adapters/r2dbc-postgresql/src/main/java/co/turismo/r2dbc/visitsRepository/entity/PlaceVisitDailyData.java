package co.turismo.r2dbc.visitsRepository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Table("place_visit_daily")
public class PlaceVisitDailyData {
    @Column("day")      private LocalDate day;
    @Column("place_id") private Long placeId;
    @Column("visits")   private Integer visits;
}