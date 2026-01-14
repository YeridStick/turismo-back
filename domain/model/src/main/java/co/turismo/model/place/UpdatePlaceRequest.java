package co.turismo.model.place;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UpdatePlaceRequest {
    private String name;
    private String description;
    private Long   categoryId;
    private Double lat;
    private Double lng;
    private String address;
    private String phone;
    private String website;
    private String[] imageUrls;
    private String[] model3dUrls;
}
