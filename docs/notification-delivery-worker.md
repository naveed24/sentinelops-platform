# Durable notification delivery worker

After Flyway V11, enable delivery **only** when a receiving HTTPS webhook can deduplicate the `Idempotency-Key` header:

```shell
export SENTINELOPS_NOTIFICATIONS_DELIVERY_ENABLED=true
export SENTINELOPS_NOTIFICATIONS_DELIVERY_WEBHOOK_URL=https://notifications.example.org/sentinelops
export SENTINELOPS_NOTIFICATIONS_DELIVERY_WEBHOOK_SECRET='replace-with-a-random-32-byte-or-longer-secret'
```

Spring property `sentinelops.notifications.delivery.enabled=true` opts in. The webhook URL and HMAC secret are mandatory at startup; the URL must be HTTPS with no credentials, query or fragment. The worker is **off by default**, so there is no accidental external delivery. Configure properties via Spring environment-variable relaxed binding or application configuration. The webhook receives JSON with `deliveryId`, rule/occurrence/event/service IDs, event type, severity, source, message and occurrence time.

The adapter signs the exact UTF-8 JSON body with HMAC-SHA256 and sets `X-SentinelOps-Signature: sha256=<lowercase hex>`, `X-SentinelOps-Delivery-Id` and `Idempotency-Key: sentinelops-notification-<deliveryId>`. Verify the signature with a constant-time comparison and deduplicate by idempotency key on the receiver. No redirects are followed; non-2xx responses count as failures and response bodies are never logged.

## Worker behavior

Each poll claims at most 10 due notifications, one at a time, using a database compare-and-set and a unique lease token. The short claim transaction commits before the outbound HTTP call. `PENDING` rows become `SENDING`, `attempt_count` increases, and `lease_expires_at` fences crashed workers. Success marks `SENT` with `delivered_at`; failure returns to `PENDING` with capped exponential backoff (default 30 seconds to 1 hour), or becomes terminal `FAILED` after 5 attempts. An expired `SENDING` lease is eligible for reclamation, and exhausted expired leases become `FAILED`. A stale worker cannot overwrite a newer lease.

Defaults: poll delay 5 seconds, batch size 10, lease 1 minute, webhook timeout 10 seconds, max attempts 5. Ensure lease exceeds the HTTP timeout plus processing overhead. Properties: `sentinelops.notifications.delivery.poll-delay-ms`, `batch-size`, `lease`, `max-attempts`, `retry-base`, `retry-cap`, `webhook.timeout`. Metrics: `sentinelops.notification.delivery` counter with bounded `result` tags `sent`, `retry`, `failed`, `stale`.

**Guarantees:** At-least-once external delivery, not exactly once. A crash after HTTP success but before database acknowledgement can resend the same notification. Receivers **must** deduplicate on the stable idempotency key. A permanently failed notification is retained for operator investigation; there is no automatic manual replay API in this slice. The earlier scheduling-time per-rule cooldown remains unchanged. Operators should monitor pending, sending and failed rows, and configure alerting for persistent delivery failures.
