# Notification delivery health dashboard

The React operations console shows an authenticated, read-only notification outbox summary via `GET /api/v1/notifications/summary`. ADMIN, RESPONDER and VIEWER roles may read it; unauthenticated requests are rejected. Other notification routes are reserved for ADMIN.

The response contains `sampledAt`, counts of `pending`, `sending`, `sent` and `failed`, and `oldestPendingAgeSeconds`. A single bounded aggregate database query produces the counts and oldest pending timestamp; no raw notification entities, URLs, secrets, payloads, lease tokens or error messages leave the backend.

The dashboard refreshes the summary on login and manual refresh. The notification fetch is independent of the incident/service fetch so an unavailable notification endpoint does not erase the incident snapshot. On failure, the last successful notification summary is marked stale, rather than presenting misleading zero counts. A 401 signs the operator out. Counts represent a point-in-time database snapshot, not a streaming view or delivery-rate measurement.

Use the existing Prometheus notification outbox metrics for automated alerting and historical trends. This panel is for operator inspection; no delivery action is exposed.

## Bounded notification activity

`GET /api/v1/notifications?limit=25&status=FAILED&beforeId=123` returns `{ "items": [...], "nextBeforeId": 101 }`. Omit `status` to show all delivery states; omit `beforeId` for the newest page. `limit` must be 1–50. The next cursor is the last returned ID when older records exist, otherwise null. Rows are ordered by descending immutable notification ID, so new inserts do not shift subsequent pages. A status change between requests may change whether a row appears; the endpoint does not promise a transaction-spanning snapshot.

Each row exposes only notification ID, state, rule/service names and IDs, event type, severity, attempt count, created/next-attempt/delivered timestamps. It never exposes event message, source, webhook destination, signature, lease token, or last error. The backend performs a DTO projection (not entity serialization) and enforces VIEWER/RESPONDER/ADMIN read access. There are no mutation routes for these roles.

The dashboard shows the latest 25 entries with a status filter and a Load older action. Refreshing or changing the filter cancels in-flight requests; failures preserve clearly marked stale data for the same filter, while a filter change clears the previous filter's results. The activity table is an operator audit view, not a guarantee of delivery or a replacement for metrics.
