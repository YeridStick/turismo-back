INSERT INTO roles (role_name) VALUES ('AGENCY') ON CONFLICT DO NOTHING;

ALTER TABLE reservations
    ADD COLUMN IF NOT EXISTS agency_id BIGINT REFERENCES agencies(id) ON DELETE SET NULL;

ALTER TABLE reservations
    ALTER COLUMN status SET DEFAULT 'requested';

CREATE TABLE IF NOT EXISTS reservation_details (
    reservation_id VARCHAR(80) PRIMARY KEY REFERENCES reservations(id) ON DELETE CASCADE,
    travelers INTEGER NOT NULL,
    customer_phone VARCHAR(30),
    contact_preference VARCHAR(30) NOT NULL,
    customer_message TEXT,
    consent_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    consent_version VARCHAR(30),
    consent_accepted_at TIMESTAMPTZ,
    payment_provider VARCHAR(30),
    payment_status VARCHAR(30),
    payment_id VARCHAR(80),
    paid_at TIMESTAMPTZ,
    agency_notes TEXT,
    contacted_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO reservation_details (
    reservation_id,
    travelers,
    contact_preference,
    consent_accepted,
    payment_provider,
    payment_status,
    payment_id,
    paid_at,
    created_at,
    updated_at
)
SELECT
    id,
    1,
    'IN_APP',
    TRUE,
    CASE WHEN payment_id IS NULL THEN 'agency_managed' ELSE 'external' END,
    CASE WHEN paid_at IS NULL THEN 'pending' ELSE 'paid' END,
    payment_id,
    paid_at,
    created_at,
    updated_at
FROM reservations
ON CONFLICT (reservation_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS reservation_messages (
    id BIGSERIAL PRIMARY KEY,
    reservation_id VARCHAR(80) NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    sender_email VARCHAR(255) NOT NULL,
    sender_type VARCHAR(20) NOT NULL CHECK (sender_type IN ('CUSTOMER', 'AGENCY')),
    body TEXT NOT NULL CHECK (char_length(body) <= 2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_email VARCHAR(255) NOT NULL,
    type VARCHAR(60) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message TEXT NOT NULL,
    reservation_id VARCHAR(80) REFERENCES reservations(id) ON DELETE CASCADE,
    agency_id BIGINT REFERENCES agencies(id) ON DELETE SET NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reservations_agency_status_created
    ON reservations (agency_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reservations_user_created
    ON reservations (user_email, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reservations_package
    ON reservations (tour_package_id);

CREATE INDEX IF NOT EXISTS idx_reservation_messages_reservation_created
    ON reservation_messages (reservation_id, created_at ASC, id ASC);

CREATE INDEX IF NOT EXISTS idx_notifications_recipient_read_created
    ON notifications (recipient_email, read, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_reservation
    ON notifications (reservation_id);
