# Kafka operational-event inbox (schema v1)

The opt-in Kafka consumer reads UTF-8 JSON from `sentinelops.operational-events`,
validates the schema, resolves the registered service, and persists a durable
`operational_events` row. The UUID `eventId` is the idempotency key.

## Local setup

Start PostgreSQL and a single-node development Kafka broker:

```bash
docker compose -f docker-compose.yml -f docker-compose.ingestion.yml up -d postgres redis kafka
docker compose -f docker-compose.yml -f docker-compose.ingestion.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic sentinelops.operational-events --partitions 3 --replication-factor 1
docker compose -f docker-compose.yml -f docker-compose.ingestion.yml exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic sentinelops.operational-events-dlt --partitions 3 --replication-factor 1
```

Start the API on the host with the `ingestion` Spring profile enabled:

```bash
SPRING_PROFILES_ACTIVE=ingestion KAFKA_BOOTSTRAP_SERVERS=localhost:9092 mvn spring-boot:run
```

Set the usual PostgreSQL and JWT environment variables as needed. The listener
is **disabled by default** without the ingestion profile; the broker's advertised
address is intended for host-side API clients, not an API running inside Compose.

## Producer contract

```json
{
  "schemaVersion": 1,
  "eventId": "20e5a62c-f57a-4a5e-8d47-06c341f40b64",
  "serviceId": 1,
  "source": "prometheus",
  "type": "ERROR_RATE",
  "message": "5xx ratio above threshold",
  "occurredAt": "2026-10-22T00:00:00Z"
}
```

Use a stable UUID across retries, a positive registered service ID, a nonblank
source (<=120 chars), an uppercase underscore-separated type (<=64 chars), a
nonblank message (<=500 chars), and an ISO-8601 timestamp. The payload is capped
at 16,384 Java characters. No incident creation or alert evaluation occurs yet.

## Delivery and failure semantics

- Offsets are committed per record after the database transaction completes;
  auto-commit is disabled. Delivery is at-least-once.
- Redeliveries of the same event UUID are no-ops. A database primary key also
  prevents concurrent duplicate rows; a losing transaction is retried.
- Permanent invalid events (bad JSON, unsupported version, missing fields or
  unknown service) go directly to the source topic's `-dlt` dead-letter topic.
- Transient errors receive three retries with one-second backoff, then DLT.
  If DLT publishing fails, recovery fails rather than acknowledging data loss.
- Monitor DLT backlog and replay only after repairing producer/data issues.
- Production deployments must configure replication, retention, TLS/ACLs,
  topic provisioning and DLT monitoring; Compose is for development only.

Integration tests exercise the transactional inbox and idempotency without a broker.
