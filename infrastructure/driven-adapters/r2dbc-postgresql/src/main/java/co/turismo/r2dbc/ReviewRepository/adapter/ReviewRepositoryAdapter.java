package co.turismo.r2dbc.ReviewRepository.adapter;

import co.turismo.model.reviews.PlaceRatingSummary;
import co.turismo.model.reviews.Review;
import co.turismo.model.reviews.TopRatedPlace;
import co.turismo.model.reviews.gateways.ReviewModalRepository;
import co.turismo.r2dbc.ReviewRepository.entity.ReviewData;
import co.turismo.r2dbc.ReviewRepository.repository.ReviewAdapterRepository;
import co.turismo.r2dbc.helper.ReactiveAdapterOperations;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Repository
public class ReviewRepositoryAdapter
        extends ReactiveAdapterOperations<Review, ReviewData, Long, ReviewAdapterRepository>
        implements ReviewModalRepository {

    private static final int MAX_LIMIT = 50;

    public ReviewRepositoryAdapter(
            ReviewAdapterRepository repository,
            ObjectMapper mapper
    ) {
        super(repository, mapper, ReviewRepositoryAdapter::toDomain);
    }

    private static Review toDomain(ReviewData data) {
        return Review.builder()
                .id(data.getId())
                .placeId(data.getPlaceId())
                .userId(data.getUserId())
                .deviceId(data.getDeviceId())
                .rating(data.getRating() == null ? 0 : data.getRating())
                .comment(data.getComment())
                .createdAt(data.getCreatedAt())
                .isVerified(Boolean.TRUE.equals(data.getIsVerified()))
                .build();
    }

    @Override
    public Flux<Review> findByPlaceId(Long placeId) {
        return repository.findByPlaceIdOrderByCreatedAtDesc(placeId)
                .map(this::toEntity);
    }

    @Override
    public Flux<Review> findByPlaceIdPaginated(Long placeId, int limit, int offset) {
        int boundedLimit = Math.min(MAX_LIMIT, Math.max(1, limit));
        int boundedOffset = Math.max(0, offset);

        return repository.findByPlaceIdPaginated(placeId, boundedLimit, boundedOffset)
                .map(this::toEntity);
    }

    @Override
    public Mono<Review> create(Review review) {
        return save(review);
    }

    @Override
    public Flux<TopRatedPlace> findTopRatedPlaces(int limit) {
        int boundedLimit = Math.min(MAX_LIMIT, Math.max(1, limit));

        return repository.findTopRatedPlaces(boundedLimit)
                .map(p -> TopRatedPlace.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .avgRating(p.getAvgRating())
                        .reviewsCount(p.getReviewsCount())
                        .build()
                );
    }

    @Override
    public Mono<PlaceRatingSummary> ratingSummary(Long placeId) {

        return repository.ratingSummary(placeId)
                .map(p -> PlaceRatingSummary.builder()
                        .placeId(p.getPlaceId())
                        .avgRating(p.getAvgRating())
                        .reviewsCount(p.getReviewsCount())
                        .build()
                )
                .switchIfEmpty(Mono.just(
                        PlaceRatingSummary.builder()
                                .placeId(placeId)
                                .avgRating(0d)
                                .reviewsCount(0)
                                .build()
                ));
    }
}