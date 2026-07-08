CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    reservation_id VARCHAR(80) NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    provider VARCHAR(30) NOT NULL,
    reference VARCHAR(120) NOT NULL UNIQUE,
    provider_transaction_id VARCHAR(120),
    checkout_url TEXT,
    amount_in_cents BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'COP',
    status VARCHAR(40) NOT NULL,
    provider_status VARCHAR(60),
    request_payload JSONB,
    response_payload JSONB,
    expires_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_reservation
    ON payment_transactions (reservation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_provider_tx
    ON payment_transactions (provider, provider_transaction_id);

CREATE TABLE IF NOT EXISTS payment_events (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    event_id VARCHAR(160),
    provider_transaction_id VARCHAR(120),
    reference VARCHAR(120),
    event_type VARCHAR(80),
    checksum VARCHAR(160),
    payload JSONB NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    processing_error TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (provider, event_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_events_wompi_fallback
    ON payment_events (provider, provider_transaction_id, event_type, checksum)
    WHERE event_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_payment_events_reference
    ON payment_events (reference);

ALTER TABLE reservation_details
    DROP CONSTRAINT IF EXISTS reservation_details_payment_status_check;

ALTER TABLE reservation_details
    ADD CONSTRAINT reservation_details_payment_status_check
    CHECK (
        payment_status IS NULL OR payment_status IN (
            'pending',
            'checkout_created',
            'processing',
            'paid',
            'failed',
            'expired',
            'refunded',
            'verified_by_agency'
        )
    );

ALTER TABLE reservation_details
    DROP CONSTRAINT IF EXISTS reservation_details_payment_provider_check;

ALTER TABLE reservation_details
    ADD CONSTRAINT reservation_details_payment_provider_check
    CHECK (
        payment_provider IS NULL OR payment_provider IN (
            'agency_managed',
            'wompi'
        )
    );
