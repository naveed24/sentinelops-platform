package com.sentinelops.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Claims one due delivery with an atomic compare-and-set update. No network I/O in this transaction. */
@Service
@RequiredArgsConstructor
public class NotificationOutboxClaimService {
    private final AlertNotificationOutboxRepository outbox;

    public record Claim(long id, String leaseToken, int attemptCount,
                        NotificationDeliveryPayload payload) {}

    @Transactional
    public Optional<Claim> claimNext(Instant now, int maxAttempts, Duration lease) {
        outbox.failExhausted(now, maxAttempts, AlertNotificationStatus.PENDING,
                AlertNotificationStatus.SENDING, AlertNotificationStatus.FAILED,
                "Delivery attempts exhausted after worker lease expiry");

        // Portable across PostgreSQL and H2; conditional UPDATE is the final
        // concurrency guard when multiple workers select the same candidate.
        var ids = outbox.findDueIds(now, maxAttempts,
                AlertNotificationStatus.PENDING, AlertNotificationStatus.SENDING,
                PageRequest.of(0, 32));
        for (Long id : ids) {
            String token = UUID.randomUUID().toString();
            int changed = outbox.claim(id, now, now.plus(lease), token, maxAttempts,
                    AlertNotificationStatus.PENDING, AlertNotificationStatus.SENDING);
            if (changed != 1) continue;

            var notification = outbox.findById(id).orElseThrow();
            var occurrence = notification.getOccurrence();
            var event = occurrence.getEvent();
            var payload = new NotificationDeliveryPayload(
                    id, notification.getRule().getId(), occurrence.getId(),
                    event.getEventId(), event.getService().getId(),
                    event.getEventType(), occurrence.getSeverity().name(),
                    event.getSource(), event.getMessage(), event.getOccurredAt());
            return Optional.of(new Claim(id, token, notification.getAttemptCount(), payload));
        }
        return Optional.empty();
    }
}
