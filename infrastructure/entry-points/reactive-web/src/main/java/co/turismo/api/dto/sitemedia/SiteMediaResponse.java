package co.turismo.api.dto.sitemedia;

import co.turismo.model.sitemedia.SiteMedia;
import co.turismo.model.sitemedia.SiteMediaAccess;

import java.time.OffsetDateTime;

public record SiteMediaResponse(
        Long id,
        Long siteId,
        String category,
        String objectKey,
        String contentType,
        String originalFilename,
        Long sizeBytes,
        Integer width,
        Integer height,
        String checksum,
        String url,
        OffsetDateTime urlExpiresAt
) {
    public static SiteMediaResponse from(SiteMedia media, SiteMediaAccess access) {
        return new SiteMediaResponse(media.getId(), media.getSiteId(), media.getCategory(), media.getObjectKey(),
                media.getContentType(), media.getOriginalFilename(), media.getSizeBytes(), media.getWidth(),
                media.getHeight(), media.getChecksum(), access.url(), access.expiresAt());
    }
}
