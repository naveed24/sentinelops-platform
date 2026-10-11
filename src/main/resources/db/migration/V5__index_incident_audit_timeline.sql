CREATE INDEX idx_incident_audit_timeline
  ON incident_audit_events(incident_id, created_at, id);
