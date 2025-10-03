package co.turismo.model.reviews;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class PlaceRatingSummary {
    private Long placeId;
    private double avgRating;
    private long reviewsCount;
}