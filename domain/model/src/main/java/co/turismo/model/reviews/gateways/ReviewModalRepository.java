package co.turismo.model.reviews.gateways;

import co.turismo.model.reviews.PlaceRatingSummary;
import co.turismo.model.reviews.Review;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReviewModalRepository {
    Flux<Review> findByPlaceId(Long placeId);
    Flux<Review> findByPlaceIdPaginated(Long placeId, int limit, int offset);
    Mono<Review> create(Review review);
    Mono<PlaceRatingSummary> ratingSummary(Long placeId);
}