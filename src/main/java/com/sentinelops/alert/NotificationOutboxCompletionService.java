package com.sentinelops.alert;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** A stale worker can never acknowledge or overwrite a newer worker's lease. */
@Service
@RequiredArgsConstructor
public class NotificationOutboxCompletionService {
    private final AlertNotificationOutboxRepository outbox;

    @Transactional
    public boolean sent(long id, String token, Instant now) {
        return outbox.markSent(id, token, AlertNotificationStatus.SENT,
                AlertNotificationStatus.SENDING, now) == 1;
    }

    @Transactional
    public boolean failed(long id, String token, boolean terminal,
                          Instant nextAttempt, String reason) {
        return outbox.markFailure(id, token, AlertNotificationStatus.SENDING,
                terminal ? AlertNotificationStatus.FAILED : AlertNotificationStatus.PENDING,
                nextAttempt, reason) == 1;
    }
}
