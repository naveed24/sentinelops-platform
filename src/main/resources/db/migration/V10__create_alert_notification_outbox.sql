CREATE TABLE alert_notification_outbox (
  id BIGSERIAL PRIMARY KEY,
  rule_id BIGINT NOT NULL REFERENCES alert_rules(id),
  occurrence_id BIGINT NOT NULL REFERENCES alert_occurrences(id),
  status VARCHAR(16) NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_alert_notification_outbox_occurrence UNIQUE (occurrence_id),
  CONSTRAINT ck_alert_notification_attempt_count CHECK (attempt_count >= 0),
  CONSTRAINT ck_alert_notification_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_alert_notification_outbox_pending
  ON alert_notification_outbox(status, next_attempt_at, id);
CREATE INDEX idx_alert_notification_outbox_rule_cooldown
  ON alert_notification_outbox(rule_id, created_at DESC, id DESC);
