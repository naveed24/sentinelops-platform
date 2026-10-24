CREATE TABLE alert_rules (
  id BIGSERIAL PRIMARY KEY,
  service_id BIGINT NOT NULL REFERENCES services(id),
  name VARCHAR(120) NOT NULL,
  canonical_name VARCHAR(120) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  enabled BOOLEAN NOT NULL,
  cooldown_seconds INTEGER NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_alert_rules_service_canonical_name UNIQUE (service_id, canonical_name),
  CONSTRAINT ck_alert_rules_cooldown CHECK (cooldown_seconds BETWEEN 0 AND 86400),
  CONSTRAINT ck_alert_rules_event_type CHECK (event_type ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE INDEX idx_alert_rules_service_enabled ON alert_rules(service_id, enabled);
