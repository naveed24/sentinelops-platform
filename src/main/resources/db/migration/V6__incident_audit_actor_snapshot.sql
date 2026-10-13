-- Preserve historical events without an actor; they predate identity attribution.
-- Do not FK to app_users: deleting or anonymizing an account must not delete history.
ALTER TABLE incident_audit_events
    ADD COLUMN actor_user_id BIGINT,
    ADD COLUMN actor_email VARCHAR(254);
