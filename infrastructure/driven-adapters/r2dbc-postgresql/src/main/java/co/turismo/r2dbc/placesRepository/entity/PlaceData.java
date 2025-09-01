package co.turismo.r2dbc.placesRepository.entity;

import lombok.*;
import org.springframework.data.annotation.Id;

import java.time.OffsetDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PlaceData {
    @Id
    private Long id;
    private Long ownerUserId;
    private String name;
    private String description;
    private String category;
    private Double lat;
    private Double lng;
    private String address;
    private String phone;
    private String website;
    private Boolean isVerified;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private Double distanceMeters;
}