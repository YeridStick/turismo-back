CREATE TABLE IF NOT EXISTS reservations (
    id VARCHAR(80) PRIMARY KEY,
    user_email VARCHAR(255),
    tour_package_id BIGINT REFERENCES tour_packages(id) ON DELETE SET NULL,
    package_title TEXT,
    total_amount NUMERIC(14, 2),
    currency VARCHAR(3) NOT NULL DEFAULT 'COP',
    start_date DATE,
    end_date DATE,
    status VARCHAR(40) NOT NULL DEFAULT 'pending_payment',
    payment_id VARCHAR(80),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS package_title TEXT,
    ADD COLUMN IF NOT EXISTS total_amount NUMERIC(14, 2),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'COP',
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS end_date DATE;

ALTER TABLE reservations
    ALTER COLUMN status SET DEFAULT 'pending_payment';

CREATE INDEX IF NOT EXISTS idx_reservations_payment_id
    ON reservations (payment_id);

CREATE INDEX IF NOT EXISTS idx_reservations_tour_package_id
    ON reservations (tour_package_id);

CREATE INDEX IF NOT EXISTS idx_reservations_user_email
    ON reservations (user_email);

CREATE INDEX IF NOT EXISTS idx_reservations_status
    ON reservations (status);

CREATE INDEX IF NOT EXISTS idx_reservations_start_end_date
    ON reservations (start_date, end_date);
