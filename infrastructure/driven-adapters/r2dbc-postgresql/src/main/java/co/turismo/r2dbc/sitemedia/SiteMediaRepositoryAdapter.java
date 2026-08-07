package co.turismo.r2dbc.sitemedia;

import co.turismo.model.sitemedia.SiteMedia;
import co.turismo.model.sitemedia.gateways.SiteMediaRepository;
import io.r2dbc.spi.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Repository
@RequiredArgsConstructor
public class SiteMediaRepositoryAdapter implements SiteMediaRepository {
    private final DatabaseClient db;

    @Override
    public Mono<SiteMedia> save(SiteMedia media) {
        String sql = """
                INSERT INTO site_media (
                    site_id, category, object_key, content_type, original_filename,
                    size_bytes, width, height, duration_seconds, checksum, created_at
                ) VALUES (
                    :siteId, :category, :objectKey, :contentType, :originalFilename,
                    :sizeBytes, :width, :height, :durationSeconds, :checksum, COALESCE(:createdAt, NOW())
                )
                RETURNING id, site_id, category, object_key, content_type, original_filename,
                    size_bytes, width, height, duration_seconds, checksum, created_at
                """;
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql)
                .bind("siteId", media.getSiteId())
                .bind("category", media.getCategory())
                .bind("objectKey", media.getObjectKey())
                .bind("contentType", media.getContentType())
                .bind("sizeBytes", media.getSizeBytes())
                .bindNull("createdAt", OffsetDateTime.class);
        spec = nullable(spec, "originalFilename", media.getOriginalFilename(), String.class);
        spec = nullable(spec, "width", media.getWidth(), Integer.class);
        spec = nullable(spec, "height", media.getHeight(), Integer.class);
        spec = nullable(spec, "durationSeconds", media.getDurationSeconds(), Long.class);
        spec = nullable(spec, "checksum", media.getChecksum(), String.class);
        return spec.map((row, metadata) -> map(row)).one();
    }

    @Override
    public Flux<SiteMedia> findBySiteId(Long siteId) {
        return db.sql(selectSql() + " WHERE site_id = :siteId ORDER BY created_at DESC, id DESC")
                .bind("siteId", siteId)
                .map((row, metadata) -> map(row))
                .all();
    }

    @Override
    public Mono<SiteMedia> findByIdForSite(Long id, Long siteId) {
        return db.sql(selectSql() + " WHERE id = :id AND site_id = :siteId")
                .bind("id", id)
                .bind("siteId", siteId)
                .map((row, metadata) -> map(row))
                .one();
    }

    @Override
    public Mono<Boolean> deleteByIdForSite(Long id, Long siteId) {
        return db.sql("DELETE FROM site_media WHERE id = :id AND site_id = :siteId")
                .bind("id", id)
                .bind("siteId", siteId)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0);
    }

    private static String selectSql() {
        return """
                SELECT id, site_id, category, object_key, content_type, original_filename,
                    size_bytes, width, height, duration_seconds, checksum, created_at
                FROM site_media
                """;
    }

    private static SiteMedia map(Row row) {
        return SiteMedia.builder()
                .id(row.get("id", Long.class))
                .siteId(row.get("site_id", Long.class))
                .category(row.get("category", String.class))
                .objectKey(row.get("object_key", String.class))
                .contentType(row.get("content_type", String.class))
                .originalFilename(row.get("original_filename", String.class))
                .sizeBytes(row.get("size_bytes", Long.class))
                .width(row.get("width", Integer.class))
                .height(row.get("height", Integer.class))
                .durationSeconds(row.get("duration_seconds", Long.class))
                .checksum(row.get("checksum", String.class))
                .createdAt(row.get("created_at", OffsetDateTime.class))
                .build();
    }

    private static <T> DatabaseClient.GenericExecuteSpec nullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
