ALTER TABLE alert_notification_outbox
  ADD COLUMN lease_token VARCHAR(36),
  ADD COLUMN lease_expires_at TIMESTAMPTZ,
  ADD COLUMN delivered_at TIMESTAMPTZ,
  ADD COLUMN last_error VARCHAR(300);

ALTER TABLE alert_notification_outbox
  ADD CONSTRAINT ck_alert_notification_lease_pair
    CHECK ((lease_token IS NULL) = (lease_expires_at IS NULL));

CREATE INDEX idx_alert_notification_outbox_expired_leases
  ON alert_notification_outbox(status, lease_expires_at, id);
