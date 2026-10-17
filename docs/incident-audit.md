# Incident audit timeline

The incident history endpoint returns audit events in chronological order, with event ID as a deterministic tie-breaker when multiple events have the same timestamp. Migration V5 adds a composite PostgreSQL index on incident ID, timestamp and event ID to support this access pattern.

Each accepted transition is persisted in the same transaction as its audit event; invalid transitions do not append history. This timeline is an application audit log, not a tamper-proof ledger.


## Actor attribution (V6)

Incident creation and accepted status transitions now snapshot the authenticated actor's user ID and email. The `GET /api/v1/incidents/{id}/history` response includes `actorUserId` and `actorEmail` per event. The server derives these values from the validated JWT principal, never from request payloads. A user who changes their email does not silently rewrite past audit events.

The two columns are nullable for existing rows and internal/service-originated operations that execute outside an HTTP authentication context; those events report `null` for both fields. The migration deliberately does not add a cascading user foreign key so historical audit records survive account changes or removal. Production endpoints still require an authenticated user under the Spring Security rules.

Actor attribution is not an authorization substitute or an immutable forensic ledger. In particular, direct access to the underlying database must be controlled and separately audited.

## Concurrent status transitions

For operator-facing status updates, send `expectedVersion` from the latest incident GET response along with the target status and message:

```json
{"status":"ACKNOWLEDGED","message":"On-call accepted","expectedVersion":0}
```

If another responder changes the incident first, the stale operation returns **HTTP 409 Conflict** with a fetch-and-retry message. A rejected operation does not append an audit event. The server also flushes Hibernate's optimistic-lock update before inserting the audit event so concurrent writers cannot record a losing transition. Legacy clients may omit `expectedVersion`, but providing it is recommended for user-driven updates; database optimistic locking still protects physically concurrent writes.

## Bounded history pages

`GET /api/v1/incidents/{id}/history?page=0&size=100` returns audit events in
chronological order, using event ID to break timestamp ties. Both parameters are optional:
`page` defaults to 0 and `size` defaults to 100. The maximum page size is 200.
Invalid page or size values return HTTP 400. The response remains a JSON array;
request subsequent pages until an empty or shorter-than-`size` page is returned.

Pagination bounds database work per request, but offset-based pages can shift when new
events arrive while a client is walking the timeline. Consumers that require a consistent
forensic snapshot should perform a separate export with an appropriate isolation policy.

## Bounded incident search pages

`GET /api/v1/incidents?page=0&limit=100` returns incidents newest first, with
incident ID as a stable tie-breaker for identical creation timestamps. `page`
defaults to 0, and `limit` defaults to 100 (maximum 200). The existing
`status`, `severity`, and `serviceId` filters apply **before** pagination.
Clients can request successive zero-based pages until a page contains fewer
than `limit` items. The response stays a JSON array for backwards compatibility.

Negative pages, limits outside 1–200, and page offsets exceeding the database
integer offset range return HTTP 400. These offset pages do not provide a
snapshot across concurrent incident inserts; for consistent exports, use a
separate snapshot-based workflow.
