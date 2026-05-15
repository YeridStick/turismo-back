package co.turismo.r2dbc.ReviewRepository.entity.view;

public interface TopRatedPlaceProjection {
    Long getId();
    String getName();
    String getDescription();
    Double getAvgRating();
    Long getReviewsCount();
}