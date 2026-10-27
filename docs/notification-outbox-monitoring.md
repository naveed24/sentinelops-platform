# Notification outbox monitoring

The outbox sampler runs independently of webhook delivery (including when delivery is disabled). It refreshes a bounded aggregate database snapshot every 30 seconds; Prometheus scrapes only in-memory gauges, so scrape traffic cannot overload the outbox database.

Metrics exported through `/actuator/prometheus` (subject to existing actuator access controls):

| Micrometer meter | Meaning |
| --- | --- |
| `sentinelops.notification.outbox.depth{status="pending|sending|sent|failed"}` | Number of durable outbox records in each state |
| `sentinelops.notification.outbox.oldest.pending.age` (seconds) | Seconds since the oldest pending record was created; zero when none |
| `sentinelops.notification.outbox.last.successful.sample` (epoch seconds) | Time of the last successful database sample |
| `sentinelops.notification.outbox.sample{result="success|error"}` | Sampling successes and failures |
| `sentinelops.notification.delivery{result="sent|retry|failed|stale"}` | Existing worker attempt outcomes; only emitted when delivery is enabled |

Micrometer's Prometheus naming convention converts dots to underscores and appends units / counter suffixes. Example alerts (adjust thresholds to traffic and configured poll/retry windows):

```promql
sentinelops_notification_outbox_depth{status="failed"} > 0
sentinelops_notification_outbox_oldest_pending_age_seconds > 300
time() - sentinelops_notification_outbox_last_successful_sample_seconds > 120
increase(sentinelops_notification_outbox_sample_total{result="error"}[10m]) > 0
```

The oldest-pending gauge measures age since enqueue, including retry backoff; it is **not** a due-now count. `SENDING` may include active or expired leases. Gauges represent a point-in-time snapshot, not a transactional cross-table view. On database errors, the previous snapshot is retained, the error counter increments, and the last-success timestamp stops advancing. Monitor snapshot freshness before interpreting counts. Metrics use only fixed enum labels—no rule IDs, event IDs, webhook URLs or payload data.

Override `sentinelops.notifications.monitor.poll-delay-ms` and `sentinelops.notifications.monitor.initial-delay-ms` if necessary; default 30000 ms for each. Do not set aggressive intervals against a large production outbox. Keep a retention/archival policy for historical SENT/FAILED rows; those gauges are cumulative stored row counts, not rates.
