package com.sentinelops.alert;

import java.time.Instant;

/** Stable webhook contract; deliveryId is the provider idempotency key. */
public record NotificationDeliveryPayload(
        long deliveryId,
        long ruleId,
        long occurrenceId,
        String eventId,
        long serviceId,
        String eventType,
        String severity,
        String source,
        String message,
        Instant occurredAt) {
}
