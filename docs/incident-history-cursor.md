# Keyset pagination for incident audit timelines

For a continuously updating timeline, use
`GET /api/v1/incidents/{id}/history/cursor?size=100`. This additive endpoint
returns the same audit-event JSON array as the offset endpoint, ordered by
`createdAt ASC, id ASC`, but avoids offset drift and deep offset scans.

For the next page, take **both** `createdAt` and `id` from the last event:

```text
GET /api/v1/incidents/42/history/cursor?size=100&afterCreatedAt=2026-10-19T00%3A00%3A00Z&afterId=500
```

`afterCreatedAt` and `afterId` must be supplied together; `afterId` must
be positive, and `size` must be between 1 and 200. Invalid cursor arguments
return HTTP 400; an unknown incident returns HTTP 404. An empty page means
there are currently no later events. The endpoint uses the same read access
policy as other incident history routes.

The cursor is a timestamp/ID tuple rather than a numeric offset, so appending
newer events will not shift previously visited pages. It is **not** a
transactional snapshot: late/backdated inserts earlier than the cursor are
not returned. The existing offset-based history endpoint remains available
for backwards compatibility.
