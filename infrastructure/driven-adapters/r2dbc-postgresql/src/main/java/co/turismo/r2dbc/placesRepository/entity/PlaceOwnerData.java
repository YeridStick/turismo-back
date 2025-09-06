package co.turismo.r2dbc.placesRepository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table("place_owners")
public class PlaceOwnerData {

    @Id
    private Long id;

    @Column("place_id")
    private Long placeId;

    @Column("user_id")
    private Long userId;
}