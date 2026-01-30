ALTER TABLE outbox_events
ADD COLUMN next_run_at TIMESTAMP NULL AFTER retry_count,
ADD COLUMN last_error TEXT NULL AFTER next_run_at;

UPDATE outbox_events
SET next_run_at = CURRENT_TIMESTAMP
WHERE next_run_at IS NULL;