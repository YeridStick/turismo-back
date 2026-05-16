-- V4: Regla de visitas por 24h + favoritos de usuario

-- Tabla de favoritos por usuario
CREATE TABLE IF NOT EXISTS user_place_favorites (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    place_id BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, place_id)
);

-- Índices para listado de favoritos
CREATE INDEX IF NOT EXISTS idx_user_place_favorites_user_created_at
    ON user_place_favorites(user_id, created_at DESC);

-- Índices para validar regla "una visita por sitio cada 24h"
CREATE INDEX IF NOT EXISTS idx_place_visits_user_place_confirmed_at
    ON place_visits(user_id, place_id, confirmed_at DESC)
    WHERE status = 'confirmed' AND user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_place_visits_device_place_confirmed_at
    ON place_visits(device_id, place_id, confirmed_at DESC)
    WHERE status = 'confirmed' AND device_id IS NOT NULL;
