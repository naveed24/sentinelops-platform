CREATE TABLE alert_occurrences (
  id BIGSERIAL PRIMARY KEY,
  rule_id BIGINT NOT NULL REFERENCES alert_rules(id),
  event_id VARCHAR(36) NOT NULL REFERENCES operational_events(event_id),
  severity VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_alert_occurrences_rule_event UNIQUE (rule_id, event_id)
);

CREATE INDEX idx_alert_occurrences_event ON alert_occurrences(event_id);
CREATE INDEX idx_alert_occurrences_rule_time ON alert_occurrences(rule_id, created_at DESC);
