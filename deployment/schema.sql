-- ==========================================================
-- Esquema de Base de Datos para Turismo (Supabase/PostgreSQL)
-- Versión: 2.1 (Soporte para PostGIS y Búsquedas Espaciales)
-- ==========================================================

-- 0. Extensiones Requeridas
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- 1. Tabla de Usuarios
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT,
    email_verified BOOLEAN DEFAULT FALSE,
    email_verification_token_hash TEXT,
    email_verification_expires_at TIMESTAMPTZ,
    recovery_code_hash TEXT,
    recovery_expires_at TIMESTAMPTZ,
    recovery_attempts INTEGER DEFAULT 0,
    recovery_max_attempts INTEGER DEFAULT 5,
    url_avatar TEXT,
    identification_type VARCHAR(50),
    identification_number VARCHAR(50),
    otp_hash TEXT,
    otp_expires_at TIMESTAMPTZ,
    otp_attempts INTEGER DEFAULT 0,
    otp_max_attempts INTEGER DEFAULT 5,
    totp_secret_encrypted TEXT,
    totp_enabled BOOLEAN DEFAULT FALSE,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Asegurar columnas si ya existe la tabla
DO $$ 
BEGIN 
    -- Seguridad Básica
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='password_hash') THEN
        ALTER TABLE users ADD COLUMN password_hash TEXT;
    END IF;

    -- TOTP 2FA
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='totp_enabled') THEN
        ALTER TABLE users ADD COLUMN totp_enabled BOOLEAN DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='totp_secret_encrypted') THEN
        ALTER TABLE users ADD COLUMN totp_secret_encrypted TEXT;
    END IF;

    -- Verificación de Email
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='email_verified') THEN
        ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='email_verification_token_hash') THEN
        ALTER TABLE users ADD COLUMN email_verification_token_hash TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='email_verification_expires_at') THEN
        ALTER TABLE users ADD COLUMN email_verification_expires_at TIMESTAMPTZ;
    END IF;

    -- Recuperación de Contraseña
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='recovery_code_hash') THEN
        ALTER TABLE users ADD COLUMN recovery_code_hash TEXT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='recovery_expires_at') THEN
        ALTER TABLE users ADD COLUMN recovery_expires_at TIMESTAMPTZ;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='recovery_attempts') THEN
        ALTER TABLE users ADD COLUMN recovery_attempts INTEGER DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='recovery_max_attempts') THEN
        ALTER TABLE users ADD COLUMN recovery_max_attempts INTEGER DEFAULT 5;
    END IF;
END $$;

-- 2. Tabla de Roles
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    role_name VARCHAR(50) UNIQUE NOT NULL
);

INSERT INTO roles (role_name) VALUES ('ADMIN'), ('OWNER'), ('VISITOR') ON CONFLICT DO NOTHING;

-- 3. Relación Usuario-Rol
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    role_id INTEGER REFERENCES roles(id) ON DELETE CASCADE,
    UNIQUE(user_id, role_id)
);

-- 4. Categorías de Lugares
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 5. Lugares Turísticos
CREATE TABLE IF NOT EXISTS places (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id BIGINT REFERENCES categories(id),
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    address TEXT,
    phone VARCHAR(50),
    website TEXT,
    image_urls TEXT[], 
    model_3d_urls TEXT[], 
    is_verified BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    verified_by BIGINT REFERENCES users(id),
    verified_at TIMESTAMPTZ,
    geom GEOMETRY(Point, 4326),
    services TEXT[], -- Lista de servicios/amenidades (WiFi, Parqueadero, etc.)
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Asegurar columnas si ya existe la tabla
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='places' AND column_name='geom') THEN
        ALTER TABLE places ADD COLUMN geom GEOMETRY(Point, 4326);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='places' AND column_name='verified_by') THEN
        ALTER TABLE places ADD COLUMN verified_by BIGINT REFERENCES users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='places' AND column_name='verified_at') THEN
        ALTER TABLE places ADD COLUMN verified_at TIMESTAMPTZ;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='places' AND column_name='services') THEN
        ALTER TABLE places ADD COLUMN services TEXT[];
    END IF;
END $$;

-- Crear índice espacial si no existe
CREATE INDEX IF NOT EXISTS places_geom_idx ON places USING GIST (geom);

-- 6. Agencias
CREATE TABLE IF NOT EXISTS agencies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    phone VARCHAR(50),
    email VARCHAR(255),
    website TEXT,
    logo_url TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 6.1 Relación Agencia-Usuario (Join Table)
CREATE TABLE IF NOT EXISTS agency_users (
    agency_id BIGINT REFERENCES agencies(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (agency_id, user_id),
    UNIQUE (user_id)
);

-- 7. Paquetes Turísticos
CREATE TABLE IF NOT EXISTS tour_packages (
    id BIGSERIAL PRIMARY KEY,
    agency_id BIGINT REFERENCES agencies(id),
    agency_name VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    city VARCHAR(100),
    description TEXT,
    days INTEGER,
    nights INTEGER,
    people VARCHAR(50),
    rating DOUBLE PRECISION DEFAULT 0.0,
    reviews BIGINT DEFAULT 0,
    price BIGINT,
    original_price BIGINT,
    discount VARCHAR(50),
    tag VARCHAR(50),
    includes TEXT[], 
    image TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 7.1 Tabla intermedia Paquetes-Lugares (Join Table)
CREATE TABLE IF NOT EXISTS tour_package_places (
    package_id BIGINT REFERENCES tour_packages(id) ON DELETE CASCADE,
    place_id BIGINT REFERENCES places(id) ON DELETE CASCADE,
    PRIMARY KEY (package_id, place_id)
);

-- 7.2 Ventas de Paquetes (Dashboard)
CREATE TABLE IF NOT EXISTS tour_package_sales (
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT REFERENCES tour_packages(id) ON DELETE SET NULL,
    agency_id BIGINT REFERENCES agencies(id) ON DELETE CASCADE,
    quantity INTEGER DEFAULT 1,
    total_amount BIGINT NOT NULL,
    sold_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_agency_date ON tour_package_sales (agency_id, sold_at);

-- 8. Reseñas de Lugares
CREATE TABLE IF NOT EXISTS place_reviews (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT REFERENCES places(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES users(id),
    device_id VARCHAR(255),
    rating SMALLINT CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 9. Feedback de Usuarios
CREATE TABLE IF NOT EXISTS place_feedback (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT REFERENCES places(id),
    user_id BIGINT REFERENCES users(id),
    device_id VARCHAR(255),
    type VARCHAR(50), 
    message TEXT,
    contact_email VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 10. Seguimiento de Visitas
CREATE TABLE IF NOT EXISTS place_visits (
    id BIGSERIAL PRIMARY KEY,
    place_id BIGINT REFERENCES places(id),
    user_id BIGINT REFERENCES users(id),
    device_id VARCHAR(255),
    started_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMPTZ,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    confirmed_on_utc DATE,
    status VARCHAR(50), 
    distance_m INTEGER,
    accuracy_m INTEGER,
    meta JSONB 
);



CREATE TABLE IF NOT EXISTS audit_log (
     id             BIGSERIAL PRIMARY KEY,
     tabla          TEXT NOT NULL,
     registro_id    BIGINT NOT NULL,
     usuario_email  TEXT,
     roles          TEXT[],
     fecha          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
     datos          JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_log_fecha  ON audit_log (fecha DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_tabla  ON audit_log (tabla, fecha DESC);

-- Asegurar columnas si ya existe la tabla
DO $$ 
BEGIN 
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='place_visits' AND column_name='lat') THEN
        ALTER TABLE place_visits ADD COLUMN lat DOUBLE PRECISION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='place_visits' AND column_name='lng') THEN
        ALTER TABLE place_visits ADD COLUMN lng DOUBLE PRECISION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='place_visits' AND column_name='confirmed_on_utc') THEN
        ALTER TABLE place_visits ADD COLUMN confirmed_on_utc DATE;
    END IF;
END $$;

-- 11. Estadísticas Diarias
CREATE TABLE IF NOT EXISTS place_visit_daily (
    day DATE NOT NULL,
    place_id BIGINT REFERENCES places(id),
    visits INTEGER DEFAULT 0,
    PRIMARY KEY (day, place_id)
);

-- 12. Vista para Resumen de Ratings
CREATE OR REPLACE VIEW place_rating_summary AS
SELECT 
    place_id, 
    AVG(rating)::DOUBLE PRECISION as avg_rating, 
    COUNT(id)::BIGINT as reviews_count
FROM place_reviews
GROUP BY place_id;
