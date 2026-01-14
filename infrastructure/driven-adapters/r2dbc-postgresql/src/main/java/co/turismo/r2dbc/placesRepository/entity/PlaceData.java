package co.turismo.r2dbc.placesRepository.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("places")
public class PlaceData {

    @Id
    private Long id;

    @Column("owner_user_id")
    private Long ownerUserId;

    private String name;
    private String description;

    @Column("category_id")
    private Long categoryId;

    private Double lat;
    private Double lng;

    private String address;
    private String phone;
    private String website;

    @Column("image_urls")
    private String[] imageUrls;

    @Column("model_3d_urls")
    private String[] model3dUrls;

    @Column("is_verified")
    private Boolean isVerified;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private OffsetDateTime createdAt;

    private Double distanceMeters;
}
