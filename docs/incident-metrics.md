# Incident lifecycle metrics

`sentinelops.incident.lifecycle` counts committed incident creations and transitions.
Prometheus exports `sentinelops_incident_lifecycle_total`.

Tags: `operation` (`created` or `transition`), `severity` (enum), `from` (`NONE` on creation), and `to` (status enum). IDs and free-form strings are excluded to keep cardinality bounded.

Events are counted after successful database commit. Rejected transitions and rollbacks are excluded. Counters are in-memory signals that reset on restart, not a replacement for the persisted audit trail.

The `/actuator/prometheus` endpoint requires ADMIN authentication.

Example PromQL: `sum by (severity) (increase(sentinelops_incident_lifecycle_total{operation="created"}[5m]))`.
