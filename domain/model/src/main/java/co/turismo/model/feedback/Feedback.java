package co.turismo.model.feedback;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data @Builder
public class Feedback {
    private Long id;
    private Long placeId;
    private Long userId;
    private String deviceId;
    private String type;
    private String message;
    private String contactEmail;
    private String status;
    private OffsetDateTime createdAt;
}