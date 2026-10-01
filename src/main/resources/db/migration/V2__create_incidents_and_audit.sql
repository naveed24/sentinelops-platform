CREATE TABLE incidents (
  id BIGSERIAL PRIMARY KEY,
  title VARCHAR(180) NOT NULL,
  description VARCHAR(2000),
  severity VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  service_id BIGINT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  resolved_at TIMESTAMPTZ,
  CONSTRAINT fk_incidents_service
    FOREIGN KEY (service_id) REFERENCES services(id)
);

CREATE TABLE incident_audit_events (
  id BIGSERIAL PRIMARY KEY,
  incident_id BIGINT NOT NULL,
  from_status VARCHAR(30),
  to_status VARCHAR(30) NOT NULL,
  message VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_incident_audit_incident
    FOREIGN KEY (incident_id) REFERENCES incidents(id) ON DELETE CASCADE
);

CREATE INDEX idx_incidents_service_id ON incidents(service_id);
CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_severity ON incidents(severity);
CREATE INDEX idx_incident_audit_incident_id ON incident_audit_events(incident_id);
