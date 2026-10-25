# Alert notification scheduling and cooldown

Every accepted operational event is matched against enabled rules for its service and exact event type. Each match always persists an immutable `alert_occurrences` audit record. The first eligible match also writes an `alert_notification_outbox` row with `PENDING` status, `attempt_count=0`, and `next_attempt_at=created_at`.

## Cooldown semantics

Cooldown is **per rule**, measured from the most recently **scheduled** notification (not from external delivery). It uses server processing time, not producer-supplied `occurredAt`, so delayed Kafka events cannot move the cooldown window backwards. When the latest outbox row is newer than `now - cooldownSeconds`, the new occurrence remains in the audit trail but no new delivery intent is queued. Zero cooldown queues each distinct event. Changing a rule's cooldown affects subsequent matches.

The evaluator takes pessimistic write locks on matching rule rows in ascending rule ID order. Concurrent Kafka consumers therefore serialize their cooldown decisions for the same rule in PostgreSQL. Inbox, occurrence and outbox writes share one transaction: a failure rolls them all back, so a Kafka retry can safely repeat the operation. Unique `event_id`, `(rule_id,event_id)` and `occurrence_id` constraints prevent duplicate records and intents.

## Delivery boundary

This slice **schedules** durable notifications but does **not send** them. No worker currently changes `PENDING` to `SENDING`, `SENT` or `FAILED`; those statuses and retry columns are reserved for a later delivery worker with provider adapters, bounded retries, leases and metrics. A queued intent can suppress subsequent intents even if delivery eventually fails; this is intentional scheduling-time cooldown, not guaranteed external notification deduplication.

Deployment: apply Flyway migration V10 before enabling Kafka ingestion. Monitor pending outbox rows until a delivery worker is deployed. Existing alert occurrences are not retroactively enqueued.
