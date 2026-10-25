# Event-driven alert occurrence persistence

An accepted Kafka operational event is evaluated against enabled rules of the same service and exact event type. Each matching rule creates one immutable `alert_occurrences` row containing the event, rule and matched severity snapshot.

The event inbox and all matches are persisted in one database transaction. On failure, Kafka retries the event. A repeated `eventId` is ignored; the `(rule_id, event_id)` unique constraint provides a second layer of deduplication.

A disabled rule, a different service or a different event type cannot match. Updating a rule affects future events only; previous severity snapshots are preserved.

Matching occurrences may also enqueue a durable notification intent, subject to each rule's cooldown. Suppressed notifications **do not** suppress audit occurrences. See [alert notification outbox](alert-notification-outbox.md) for concurrency, cooldown and delivery boundaries.
