CREATE TABLE webhook_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  partner VARCHAR(50) NOT NULL,
  event_id VARCHAR(100) NOT NULL,
  order_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  payload JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_webhook_events_event_id UNIQUE (event_id)
);

CREATE INDEX idx_webhook_events_order_id ON webhook_events(order_id);
CREATE INDEX idx_webhook_events_partner_created_at ON webhook_events(partner, created_at);
