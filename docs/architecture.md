# SentinelOps architecture

## Current architecture

```mermaid
flowchart LR
    Client[API clients / future React dashboard] --> API[Spring Boot API]
    API --> PG[(PostgreSQL)]
    API --> Redis[(Redis)]
    API --> Actuator[Actuator / Prometheus]
```

## Planned evolution

```mermaid
flowchart LR
    UI[React Dashboard] --> API[SentinelOps API]
    API --> Auth[JWT / RBAC]
    API --> PG[(PostgreSQL)]
    API --> Redis[(Redis)]
    Producers[Telemetry / webhook producers] --> Kafka[(Kafka)]
    Kafka --> Processor[Event Processor]
    Processor --> PG
    Processor --> Rules[Alert Rules]
    Rules --> Notify[Notification Adapters]
    API --> Metrics[Micrometer / Prometheus]
```

## Design goals

- Clear domain boundaries around services, incidents, events and alerts.
- Durable relational state in PostgreSQL.
- Asynchronous event processing for operational data.
- Redis for fast reads, distributed rate limiting and short-lived coordination.
- Auditable incident state transitions.
- Secure API access using JWT and role-based authorization.
- Observable by default through health probes, metrics and structured logging.
- Container-first local development and CI-friendly automated tests.
