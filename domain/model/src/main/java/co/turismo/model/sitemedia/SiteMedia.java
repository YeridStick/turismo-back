package co.turismo.model.sitemedia;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class SiteMedia {
    Long id;
    Long siteId;
    String category;
    String objectKey;
    String contentType;
    String originalFilename;
    Long sizeBytes;
    Integer width;
    Integer height;
    Long durationSeconds;
    String checksum;
    OffsetDateTime createdAt;
}
