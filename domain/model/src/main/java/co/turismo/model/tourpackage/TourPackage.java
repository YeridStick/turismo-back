package co.turismo.model.tourpackage;

import co.turismo.model.place.Place;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TourPackage {
    private Long id;
    private Long agencyId;
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
    private Long originalPrice;
    private String discount;
    private String tag;
    private String[] includes;
    private String image;

    private Boolean isActive;
    private OffsetDateTime createdAt;

    private Long[] placeIds;
    private List<Place> places;
}
