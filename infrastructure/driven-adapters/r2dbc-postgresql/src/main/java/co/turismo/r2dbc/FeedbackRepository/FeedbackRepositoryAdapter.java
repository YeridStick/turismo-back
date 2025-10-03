package co.turismo.r2dbc.FeedbackRepository;

import co.turismo.model.feedback.Feedback;
import co.turismo.model.feedback.gateways.FeedbackModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class FeedbackRepositoryAdapter implements FeedbackModelRepository {

    private final DatabaseClient db;

    private Feedback map(io.r2dbc.spi.Row row) {
        return Feedback.builder()
                .id(row.get("id", Long.class))
                .placeId(row.get("place_id", Long.class))
                .userId(row.get("user_id", Long.class))
                .deviceId(row.get("device_id", String.class))
                .type(row.get("type", String.class))
                .message(row.get("message", String.class))
                .contactEmail(row.get("contact_email", String.class))
                .status(row.get("status", String.class))
                .createdAt(row.get("created_at", OffsetDateTime.class))
                .build();
    }

    @Override
    public Mono<Feedback> create(Feedback f) {
        String sql = """
            INSERT INTO place_feedback (place_id, user_id, device_id, type, message, contact_email)
            VALUES (:placeId, :userId, :deviceId, :type, :message, :contactEmail)
            RETURNING id, place_id, user_id, device_id, type, message, contact_email, status, created_at
          """;

        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("placeId", f.getPlaceId())
                .bind("type", f.getType())
                .bind("message", f.getMessage());

        // Campos que pueden ser null:
        spec = bindNullable(spec, "userId", f.getUserId(), Long.class);
        spec = bindNullable(spec, "deviceId", f.getDeviceId(), String.class);
        spec = bindNullable(spec, "contactEmail", f.getContactEmail(), String.class);

        return spec
                .map((r, m) -> map(r))
                .one();
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

}