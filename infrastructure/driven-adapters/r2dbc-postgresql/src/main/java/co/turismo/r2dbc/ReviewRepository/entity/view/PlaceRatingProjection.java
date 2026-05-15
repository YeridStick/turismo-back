package co.turismo.r2dbc.ReviewRepository.entity.view;

public interface PlaceRatingProjection {
    Long getPlaceId();
    Double getAvgRating();
    Long getReviewsCount();
}
