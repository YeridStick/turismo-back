package co.turismo.model.sitemedia;

import java.time.OffsetDateTime;

public record SiteMediaAccess(String url, OffsetDateTime expiresAt) {
}
