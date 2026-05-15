package co.turismo.r2dbc.ReviewRepository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("place_reviews")
public class ReviewData {

    @Id
    private Long id;

    @Column("place_id")
    private Long placeId;

    @Column("user_id")
    private Long userId;

    @Column("device_id")
    private String deviceId;

    private Short rating;

    private String comment;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("is_verified")
    private Boolean isVerified;
}