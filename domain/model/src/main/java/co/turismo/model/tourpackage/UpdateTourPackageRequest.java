package co.turismo.model.tourpackage;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class UpdateTourPackageRequest {
    private String title;
    private String city;
    private String description;
    private Integer days;
    private Integer nights;
    private String people;
    private Double rating;
    private Long reviews;
    private Long price;
    private Long originalPrice;
    private String discount;
    private String tag;
    private String[] includes;
    private String image;
    private Long[] placeIds;
}
