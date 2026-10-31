package com.sentinelops.alert;

import java.time.Instant;

/**
 * Bounded, read-only delivery health snapshot. No payloads, URLs, lease tokens,
 * errors or recipient details are exposed to the operator dashboard.
 */
public record NotificationSummary(
        Instant sampledAt,
        long pending,
        long sending,
        long sent,
        long failed,
        long oldestPendingAgeSeconds) {
}
