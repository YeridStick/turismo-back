package co.turismo.r2dbc.tourpackage.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table("tour_packages")
public class TourPackageData {

    @Id
    private Long id;

    @Column("agency_id")
    private Long agencyId;

    @Column("agency_name")
    private String agencyName;

    private String title;
    private String city;
    private String description;
    private Integer days;
    private Integer nights;
    private String people;
    private Double rating;
    private Long reviews;
    private Long price;

    @Column("original_price")
    private Long originalPrice;

    private String discount;
    private String tag;
    private String[] includes;
    private String image;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("place_ids")
    private Long[] placeIds;
}
