ALTER TABLE payment_events
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(20) NOT NULL DEFAULT 'received';

UPDATE payment_events
SET processing_status = CASE WHEN processed THEN 'completed' ELSE processing_status END;

ALTER TABLE payment_events
    DROP CONSTRAINT IF EXISTS payment_events_processing_status_check;

ALTER TABLE payment_events
    ADD CONSTRAINT payment_events_processing_status_check
    CHECK (processing_status IN ('received', 'processing', 'failed', 'completed'));

ALTER TABLE reservation_messages
    ADD COLUMN IF NOT EXISTS system_event_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uq_reservation_messages_system_event
    ON reservation_messages (reservation_id, system_event_key)
    WHERE system_event_key IS NOT NULL;
