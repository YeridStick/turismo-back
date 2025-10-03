package co.turismo.r2dbc.ReviewRepository;

import co.turismo.model.reviews.PlaceRatingSummary;
import co.turismo.model.reviews.Review;
import co.turismo.model.reviews.gateways.ReviewModalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class ReviewRepositoryAdapter implements ReviewModalRepository {

    private final DatabaseClient db;

    private Review map(io.r2dbc.spi.Row row) {
        return Review.builder()
                .id(row.get("id", Long.class))
                .placeId(row.get("place_id", Long.class))
                .userId(row.get("user_id", Long.class))
                .deviceId(row.get("device_id", String.class))
                .rating(row.get("rating", Short.class))
                .comment(row.get("comment", String.class))
                .createdAt(row.get("created_at", OffsetDateTime.class))
                .isVerified(Boolean.TRUE.equals(row.get("is_verified", Boolean.class)))
                .build();
    }

    @Override
    public Flux<Review> findByPlaceId(Long placeId) {
        String sql = """
      SELECT id, place_id, user_id, device_id, rating, comment, created_at, is_verified
      FROM place_reviews
      WHERE place_id = :placeId
      ORDER BY created_at DESC
    """;
        return db.sql(sql).bind("placeId", placeId)
                .map((r, m) -> map(r)).all();
    }

    @Override
    public Mono<Review> create(Review r) {
        String sql = """
            INSERT INTO place_reviews (place_id, user_id, device_id, rating, comment)
            VALUES (:placeId, :userId, :deviceId, :rating, :comment)
            RETURNING id, place_id, user_id, device_id, rating, comment, created_at, is_verified
          """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("placeId", r.getPlaceId())
                .bind("rating", r.getRating())
                .bind("comment", r.getComment());

        spec = bindNullable(spec, "userId", r.getUserId(), Long.class);
        spec = bindNullable(spec, "deviceId", r.getDeviceId(), String.class);

        return spec
                .map((rw, m) -> map(rw))
                .one();
    }

    @Override
    public Mono<PlaceRatingSummary> ratingSummary(Long placeId) {
        String sql = """
      SELECT place_id, avg_rating, reviews_count
      FROM place_rating_summary
      WHERE place_id = :placeId
    """;
        return db.sql(sql).bind("placeId", placeId)
                .map((row, m) -> PlaceRatingSummary.builder()
                        .placeId(row.get("place_id", Long.class))
                        .avgRating(row.get("avg_rating", Double.class))
                        .reviewsCount(row.get("reviews_count", Long.class))
                        .build()
                ).one()
                .switchIfEmpty(Mono.just(
                        PlaceRatingSummary.builder()
                                .placeId(placeId).avgRating(0d).reviewsCount(0).build()
                ));
    }

    /** Helper para bindear nulls correctamente. */
    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}