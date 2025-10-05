package co.turismo.usecase.reviews;

import co.turismo.model.reviews.PlaceRatingSummary;
import co.turismo.model.reviews.Review;
import co.turismo.model.reviews.gateways.ReviewModalRepository;
import co.turismo.model.user.gateways.UserRepository;
import co.turismo.model.userIdentityPort.UserIdentityPort;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class ReviewsUseCase {

    private final ReviewModalRepository reviewRepository;
    private final UserIdentityPort userIdentityPort;

    public Mono<Review> create(Long placeId, Short rating, String comment, String email) {
        if (rating == null || rating < 1 || rating > 5)
            return Mono.error(new IllegalArgumentException("Rating inválido (1-5)"));
        if (comment == null || comment.trim().isEmpty())
            return Mono.error(new IllegalArgumentException("Comentario obligatorio"));

        return userIdentityPort.getUserIdForEmail(email)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Usuario no encontrado con email: " + email)))
                .flatMap(user -> {
                    Review review = Review.builder()
                            .placeId(placeId)
                            .userId(user.id())
                            .deviceId(null)
                            .rating(rating)
                            .comment(comment.trim())
                            .build();
                    return reviewRepository.create(review);
                });
    }


    public Flux<Review> list(Long placeId) {
        return reviewRepository.findByPlaceId(placeId);
    }

    public Mono<PlaceRatingSummary> summary(Long placeId) {
        return reviewRepository.ratingSummary(placeId);
    }
}
