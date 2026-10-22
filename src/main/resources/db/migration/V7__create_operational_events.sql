CREATE TABLE operational_events (
  event_id VARCHAR(36) PRIMARY KEY,
  service_id BIGINT NOT NULL REFERENCES services(id),
  source VARCHAR(120) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  message VARCHAR(500) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_operational_events_service_time ON operational_events(service_id, occurred_at DESC);
