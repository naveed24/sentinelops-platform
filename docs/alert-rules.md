# Alert rule management

Alert rule configuration is persisted in PostgreSQL (Flyway V8) and is available through the authenticated `/api/v1/alert-rules` API. This slice provides **configuration only**: rules are **not yet evaluated**, and no notifications are sent. The evaluation engine and notification outbox are future milestones.

## Contract

- `POST /api/v1/alert-rules` — ADMIN only; create a rule for an existing service.
- `GET /api/v1/alert-rules/{id}` — ADMIN, RESPONDER, VIEWER.
- `GET /api/v1/alert-rules?serviceId=1&page=0&size=50` — ADMIN, RESPONDER, VIEWER; sorted by ascending ID, max 100 per page.
- `PUT /api/v1/alert-rules/{id}` — ADMIN only; update an existing rule. Include its latest `expectedVersion` to prevent lost updates.

Create example (with a valid ADMIN bearer token):

```json
{
  "serviceId": 1,
  "name": "Checkout error rate",
  "eventType": "ERROR_RATE",
  "severity": "SEV2",
  "enabled": true,
  "cooldownSeconds": 300
}
```

Update uses the same fields except `serviceId`, plus `expectedVersion` from the last response. A stale version returns HTTP 409. A service cannot be reassigned by updating its rule; create a new rule for that service instead.

`eventType` is a case-sensitive event category such as `ERROR_RATE` or `LATENCY_SPIKE`, matching the Kafka operational event contract. It must match `[A-Z][A-Z0-9_]*` and be at most 64 characters. `severity` is SEV1–SEV4. `cooldownSeconds` must be 0–86400; zero means no future cooldown. `enabled=false` disables the rule without deleting its configuration.

Names are trimmed and unique per service ignoring case. The database stores a canonical lowercase name under a unique constraint to prevent concurrent duplicates. Each rule has a database-managed optimistic-lock `version`, timestamps, a service foreign key, and an index on `(service_id, enabled)` for future evaluation queries.

## Next steps

Implement rule evaluation for newly ingested operational events, durable notification outbox with idempotent delivery, per-rule cooldown enforcement, and notification channel adapters. No external side effects occur merely by creating or updating a rule.
