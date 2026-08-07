CREATE TABLE IF NOT EXISTS site_media (
    id BIGSERIAL PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    category VARCHAR(20) NOT NULL CHECK (category IN ('images', 'videos', 'models-3d')),
    object_key VARCHAR(512) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    original_filename VARCHAR(160) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    width INTEGER,
    height INTEGER,
    duration_seconds BIGINT,
    checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_site_media_site_created
    ON site_media (site_id, created_at DESC, id DESC);
