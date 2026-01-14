package co.turismo.model.tourpackage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class CreateTourPackageRequest {
    private Long agencyId;
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
