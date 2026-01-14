package co.turismo.model.place;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Place {
    private Long id;
    private Long ownerUserId;

    private String name;
    private String description;

    private Long categoryId;

    private Double lat;
    private Double lng;

    private String address;
    private String phone;
    private String website;

    private String[] imageUrls;
    private String[] model3dUrls;

    private Boolean isVerified;
    private Boolean isActive;
    private OffsetDateTime createdAt;

    private Double distanceMeters;
}
