package co.turismo.api.dto.sitemedia;

import co.turismo.model.sitemedia.SiteMedia;

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
        String checksum
) {
    public static SiteMediaResponse from(SiteMedia media) {
        return new SiteMediaResponse(media.getId(), media.getSiteId(), media.getCategory(), media.getObjectKey(),
                media.getContentType(), media.getOriginalFilename(), media.getSizeBytes(), media.getWidth(),
                media.getHeight(), media.getChecksum());
    }
}
