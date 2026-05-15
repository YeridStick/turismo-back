-- ==========================================================
-- Migración: agency_users permite múltiples agencias por usuario
-- Versión: 3.0
-- Fecha: 2026-05-15
-- Objetivo: eliminar restricción UNIQUE(user_id) para relación many-to-many
-- ==========================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'agency_users_user_id_key'
    ) THEN
        ALTER TABLE agency_users DROP CONSTRAINT agency_users_user_id_key;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_agency_users_user_id ON agency_users (user_id);
