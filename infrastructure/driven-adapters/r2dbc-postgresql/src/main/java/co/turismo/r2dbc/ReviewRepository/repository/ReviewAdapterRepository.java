package co.turismo.r2dbc.ReviewRepository.repository;

import co.turismo.r2dbc.ReviewRepository.entity.view.PlaceRatingProjection;
import co.turismo.r2dbc.ReviewRepository.entity.ReviewData;
import co.turismo.r2dbc.ReviewRepository.entity.view.TopRatedPlaceProjection;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.ReactiveQueryByExampleExecutor;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReviewAdapterRepository extends
        ReactiveCrudRepository<ReviewData, Long>,
        ReactiveQueryByExampleExecutor<ReviewData> {

    Flux<ReviewData> findByPlaceIdOrderByCreatedAtDesc(Long placeId);

    @Query("""
        SELECT
            id,
            place_id,
            user_id,
            device_id,
            rating,
            comment,
            created_at,
            is_verified
        FROM place_reviews
        WHERE place_id = :placeId
        ORDER BY created_at DESC
        LIMIT :limit
        OFFSET :offset
    """)
    Flux<ReviewData> findByPlaceIdPaginated(
            Long placeId,
            int limit,
            int offset
    );

    @Query("""
        SELECT
            p.id AS id,
            p.name AS name,
            p.description AS description,
            prs.avg_rating AS avgRating,
            prs.reviews_count AS reviewsCount
        FROM place_rating_summary prs
        JOIN places p ON p.id = prs.place_id
        WHERE prs.reviews_count > 0
        ORDER BY prs.avg_rating DESC, prs.reviews_count DESC
        LIMIT :limit
    """)
    Flux<TopRatedPlaceProjection> findTopRatedPlaces(int limit);

    @Query("""
    SELECT
        place_id,
        avg_rating,
        reviews_count
    FROM place_rating_summary
    WHERE place_id = :placeId
""")
    Mono<PlaceRatingProjection> ratingSummary(Long placeId);
}