# Notification delivery health dashboard

The React operations console shows an authenticated, read-only notification outbox summary via `GET /api/v1/notifications/summary`. ADMIN, RESPONDER and VIEWER roles may read it; unauthenticated requests are rejected. Other notification routes are reserved for ADMIN.

The response contains `sampledAt`, counts of `pending`, `sending`, `sent` and `failed`, and `oldestPendingAgeSeconds`. A single bounded aggregate database query produces the counts and oldest pending timestamp; no raw notification entities, URLs, secrets, payloads, lease tokens or error messages leave the backend.

The dashboard refreshes the summary on login and manual refresh. The notification fetch is independent of the incident/service fetch so an unavailable notification endpoint does not erase the incident snapshot. On failure, the last successful notification summary is marked stale, rather than presenting misleading zero counts. A 401 signs the operator out. Counts represent a point-in-time database snapshot, not a streaming view or delivery-rate measurement.

Use the existing Prometheus notification outbox metrics for automated alerting and historical trends. This panel is for operator inspection; no delivery action is exposed.
