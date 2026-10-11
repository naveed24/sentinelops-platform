# Incident audit timeline

The incident history endpoint returns audit events in chronological order, with event ID as a deterministic tie-breaker when multiple events have the same timestamp. Migration V5 adds a composite PostgreSQL index on incident ID, timestamp and event ID to support this access pattern.

Each accepted transition is persisted in the same transaction as its audit event; invalid transitions do not append history. This timeline is an application audit log, not a tamper-proof ledger.
