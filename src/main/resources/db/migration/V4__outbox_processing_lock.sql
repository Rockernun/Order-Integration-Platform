ALTER TABLE outbox_events
    ADD COLUMN locked_by VARCHAR(100) NULL,
    ADD COLUMN locked_at TIMESTAMP NULL;

CREATE INDEX idx_outbox_status_next_run_id
    ON outbox_events(status, next_run_at, id);

CREATE INDEX idx_outbox_locked_at
    ON outbox_events(locked_at);