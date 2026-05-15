package co.turismo.model.reviews;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopRatedPlace {
    private Long id;
    private String name;
    private String description;
    private Double avgRating;
    private Long reviewsCount;
}