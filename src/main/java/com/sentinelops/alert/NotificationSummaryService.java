package com.sentinelops.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;

/**
 * A single aggregate SQL statement supplies a consistent, bounded snapshot.
 * Unlike Prometheus sampling, this endpoint reports live database state when
 * explicitly requested by an authenticated operator.
 */
@Service
@RequiredArgsConstructor
public class NotificationSummaryService {
    private final AlertNotificationOutboxRepository outbox;

    @Transactional(readOnly = true)
    public NotificationSummary snapshot() {
        var counts = new EnumMap<AlertNotificationStatus, Long>(AlertNotificationStatus.class);
        for (var status : AlertNotificationStatus.values()) {
            counts.put(status, 0L);
        }
        Instant oldestPending = null;
        for (Object[] row : outbox.summarizeByStatus()) {
            var status = (AlertNotificationStatus) row[0];
            counts.put(status, (Long) row[1]);
            if (status == AlertNotificationStatus.PENDING) {
                oldestPending = (Instant) row[2];
            }
        }
        Instant now = Instant.now();
        long age = oldestPending == null ? 0L :
                Math.max(0L, Duration.between(oldestPending, now).getSeconds());
        return new NotificationSummary(now,
                counts.get(AlertNotificationStatus.PENDING),
                counts.get(AlertNotificationStatus.SENDING),
                counts.get(AlertNotificationStatus.SENT),
                counts.get(AlertNotificationStatus.FAILED),
                age);
    }
}
