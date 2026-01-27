CREATE INDEX idx_outbox_status_next_id
ON outbox_events (status, next_run_at, id);