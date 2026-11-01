-- Supports newest-first status-filtered operator drill-down without table scans.
CREATE INDEX idx_alert_notification_outbox_status_id
  ON alert_notification_outbox(status, id DESC);
