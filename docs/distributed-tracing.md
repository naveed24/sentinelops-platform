# Distributed tracing and operational observations

SentinelOps uses the Spring Boot 4 native OpenTelemetry starter and Micrometer Observation API. Incoming HTTP requests are automatically traced by Spring; explicit observations instrument operational-event ingestion and each attempted notification delivery. Prometheus continues to scrape metrics separately; enabling OTLP trace export does **not** enable OTLP metric export.

## Configuration

OTLP trace export is **off by default**, so local development and tests do not attempt to connect to a missing collector. To enable tracing export to a collector (Tempo, Jaeger via collector, etc.):

```bash
export OTLP_TRACING_ENABLED=true
export OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces
export TRACING_SAMPLE_PROBABILITY=0.1
```

The endpoint must be reachable by the application. Use HTTPS and appropriate network controls in production; do not send traces over the public internet to an unauthenticated endpoint. Set `TRACING_SAMPLE_PROBABILITY=1.0` only for controlled debugging with manageable traffic. Spring Boot adds trace/span correlation IDs to application logs automatically when tracing is active.

## Span names and attributes

| Span / observation | Low-cardinality `outcome` | High-cardinality trace-only fields |
| --- | --- | --- |
| `sentinelops.ingestion.event` | `accepted`, `duplicate`, `rejected`, `error` | `event.id`, `service.id` (when parsed) |
| `sentinelops.notification.attempt` | `sent`, `retry`, `failed`, `stale`, `error` | `notification.id`, `notification.attempt` |

Outcomes are bounded enums, so they are safe Prometheus metric dimensions. IDs are **high-cardinality** and never become metric labels. Raw event messages, webhook URLs, HMAC secrets, JWTs, provider error bodies, and payloads are **never** attached to spans. Errors are represented by sanitized markers.

An ingestion observation includes validation, idempotency checks and rule evaluation; the database transaction is still controlled by Spring. Each delivery observation includes the provider attempt and the fenced acknowledgement/failure update. Empty delivery polls do not create per-attempt spans.

## Boundaries and verification

The durable Kafka inbox and notification outbox are asynchronous boundaries. These observations **do not claim a single continuous parent/child trace** across a stored event and a later scheduled delivery: each delivery attempt starts a new trace unless an upstream scope exists. Persisting trace context and using span links across durable boundaries is future work.

Run `mvn test` and verify the ingestion and delivery observation tests, then enable an OTLP collector and inspect `sentinelops.ingestion.event` and `sentinelops.notification.attempt` in the trace backend. Inspect `/actuator/prometheus` using an authenticated admin account for the corresponding low-cardinality observation timers.

Reference: [Spring Boot 4 tracing](https://docs.spring.io/spring-boot/4.0/reference/actuator/tracing.html).
