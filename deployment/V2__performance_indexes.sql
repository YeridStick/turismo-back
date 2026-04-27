-- ==========================================================
-- Migración de Performance: Índices Optimizados para Búsqueda
-- Versión: 2.0
-- Fecha: 2026-04-26
-- Objetivo: Mejorar rendimiento de GET /api/places/search
-- ==========================================================

-- NOTA: Usar CONCURRENTLY para evitar bloqueo de tabla en producción
-- Requiere: CREATE EXTENSION IF NOT EXISTS pg_trgm (para índices trigram)

-- 0. Extensión necesaria para búsqueda de texto con LIKE/ILIKE
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. Índice compuesto para filtrado rápido por activo + categoría + ordenamiento
-- Uso: WHERE is_active = TRUE AND category_id = X ORDER BY created_at DESC
-- Impacto: Reduce de seq scan a index scan + sort
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_active_category_created 
ON places (is_active, category_id, created_at DESC) 
WHERE is_active = TRUE;

-- 2. Índice GiST parcial para búsquedas geográficas (solo lugares activos)
-- Uso: ST_DWithin() y consultas de proximidad con onlyNearby
-- Impacto: Evita escanear lugares inactivos en búsquedas geográficas
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_geom_active 
ON places USING GIST (geom) 
WHERE is_active = TRUE;

-- 3. Índice trigram para búsquedas de texto parciales (LIKE '%texto%')
-- Uso: Modo TEXT de búsqueda con ILIKE
-- Impacto: Permite búsquedas de texto eficientes sin Full Text Search
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_name_trgm 
ON places USING GIN (name gin_trgm_ops) 
WHERE is_active = TRUE;

-- 4. Índice trigram para dirección (búsquedas frecuentes por ubicación)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_address_trgm 
ON places USING GIN (address gin_trgm_ops) 
WHERE is_active = TRUE AND address IS NOT NULL;

-- 5. Índice para búsqueda por owner (usado en findMine y verificaciones)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_owner_active 
ON places (owner_user_id, created_at DESC) 
WHERE is_active = TRUE;

-- 6. Índice de cobertura (covering index) para consultas completas sin acceso a heap
-- Uso: Cuando se buscan todos los campos frecuentemente accesados
-- NOTA: Este índice es grande, evaluar espacio en disco antes de crear en producción
-- CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_covering_active 
-- ON places (is_active, category_id, created_at) 
-- INCLUDE (id, owner_user_id, name, description, address, phone, website, is_verified)
-- WHERE is_active = TRUE;

-- ==========================================================
-- Comandos para verificar el uso de índices (ejecutar después de carga):
-- 
-- Ver tamaño de índices:
--   SELECT schemaname, tablename, indexname, pg_size_pretty(pg_relation_size(indexrelid)) 
--   FROM pg_stat_user_indexes WHERE tablename = 'places';
--
-- Ver frecuencia de uso de índices:
--   SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
--   FROM pg_stat_user_indexes WHERE tablename = 'places' ORDER BY idx_scan DESC;
--
-- EXPLAIN ANALYZE para la query principal:
--   EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
--   SELECT * FROM places 
--   WHERE is_active = TRUE 
--   AND category_id = 1 
--   ORDER BY created_at DESC 
--   LIMIT 20;
-- ==========================================================
