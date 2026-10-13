# Incident audit timeline

The incident history endpoint returns audit events in chronological order, with event ID as a deterministic tie-breaker when multiple events have the same timestamp. Migration V5 adds a composite PostgreSQL index on incident ID, timestamp and event ID to support this access pattern.

Each accepted transition is persisted in the same transaction as its audit event; invalid transitions do not append history. This timeline is an application audit log, not a tamper-proof ledger.


## Actor attribution (V6)

Incident creation and accepted status transitions now snapshot the authenticated actor's user ID and email. The `GET /api/v1/incidents/{id}/history` response includes `actorUserId` and `actorEmail` per event. The server derives these values from the validated JWT principal, never from request payloads. A user who changes their email does not silently rewrite past audit events.

The two columns are nullable for existing rows and internal/service-originated operations that execute outside an HTTP authentication context; those events report `null` for both fields. The migration deliberately does not add a cascading user foreign key so historical audit records survive account changes or removal. Production endpoints still require an authenticated user under the Spring Security rules.

Actor attribution is not an authorization substitute or an immutable forensic ledger. In particular, direct access to the underlying database must be controlled and separately audited.
