package co.turismo.model.place;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreatePlaceRequest {
    private String ownerEmail;
    private String name;
    private String description;
    private String category;
    private Double lat;
    private Double lng;
    private String address;
    private String phone;
    private String website;
}