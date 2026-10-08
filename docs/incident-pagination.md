# Incident listing: bounded pagination

`GET /api/v1/incidents` accepts optional `status`, `severity`,
`serviceId`, `limit`, and `page` query parameters.

- `limit`: records per page, default 100, allowed 1–200.
- `page`: zero-based page index, default 0, allowed 0–1000.
- Results are ordered by `createdAt DESC, id DESC` so ties have a
  deterministic order.
- All existing filters apply before pagination. An empty array means no
  results on that page.

Example: `GET /api/v1/incidents?severity=SEV2&limit=20&page=1`
returns the second page of at most 20 SEV2 incidents.

This endpoint intentionally retains its original JSON array response for
backward compatibility. Offset pagination is appropriate for browsing, but
concurrent incident creation may shift page boundaries. A cursor-based API
should be added before using it for exactly-once event processing.
