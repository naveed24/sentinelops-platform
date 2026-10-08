# Incident concurrency

Clients should use the incident response version when submitting a status transition. A stale version should result in HTTP 409 Conflict without writing an audit event.
