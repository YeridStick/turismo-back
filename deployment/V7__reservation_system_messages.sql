ALTER TABLE reservation_messages
    DROP CONSTRAINT IF EXISTS reservation_messages_sender_type_check;

ALTER TABLE reservation_messages
    ADD CONSTRAINT reservation_messages_sender_type_check
        CHECK (sender_type IN ('CUSTOMER', 'AGENCY', 'SYSTEM'));
