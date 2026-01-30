ALTER TABLE outbox_events
    ADD COLUMN locked_by VARCHAR(100) NULL,
    ADD COLUMN locked_at TIMESTAMP NULL;
