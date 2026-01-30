CREATE INDEX idx_outbox_pending_pick
ON outbox_events (status, next_run_at, id);