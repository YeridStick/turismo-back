package co.turismo.r2dbc.placesRepository.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;

import java.time.OffsetDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PlaceData {
    @Id
    private Long id;
    private String name;
    private String description;
    private String category;
    private Double lat;
    private Double lng;
    private String address;
    private String phone;
    private String website;

    @Column("is_verified")
    private Boolean isVerified;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private OffsetDateTime createdAt;

    private Double distanceMeters;
}