package co.turismo.model.reviews;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class Review {
    private Long id;
    private Long placeId;
    private Long userId;
    private String deviceId;
    private short rating;
    private String comment;
    private OffsetDateTime createdAt;
    private boolean isVerified;
}