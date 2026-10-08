# Incident transition concurrency

Incident responses include a numeric `version`. Clients should include that
value as `expectedVersion` when changing incident status:

```http
PATCH /api/v1/incidents/42/status
Content-Type: application/json

{"status":"ACKNOWLEDGED","message":"On-call accepted","expectedVersion":0}
```

A successful transition increments the version. A stale `expectedVersion`
returns **409 Conflict** without changing the incident or adding an audit event.
Reload the incident, inspect the current status, and retry only if the
transition is still appropriate.

The version precondition is optional to preserve compatibility with existing
clients. JPA optimistic locking still protects simultaneous database updates:
the status update is flushed before the audit insert and any optimistic-lock
failure rolls back the entire transaction. Clients should send the version to
detect stale sequential requests as well.

Negative versions return **400 Bad Request**. Invalid lifecycle transitions
also return **400 Bad Request**.
