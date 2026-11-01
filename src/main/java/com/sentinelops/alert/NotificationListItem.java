package com.sentinelops.alert;

import com.sentinelops.incident.IncidentSeverity;
import java.time.Instant;

/** Safe projection for operator drill-down. No payload, destination, lease, or error text. */
public record NotificationListItem(
        Long id, AlertNotificationStatus status,
        Long ruleId, String ruleName, Long serviceId, String serviceName,
        String eventType, IncidentSeverity severity, int attemptCount,
        Instant createdAt, Instant nextAttemptAt, Instant deliveredAt) {
}
