package com.sentinelops.alert;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Duration;

/**
 * Opt-in, bounded at-least-once dispatcher. Each claim/ack is a short DB
 * transaction; network calls never hold database locks. Expired leases are
 * safely reclaimed and fenced by their unique lease tokens.
 */
public class NotificationDeliveryWorker {
    public record Settings(int batchSize, int maxAttempts, Duration lease,
                           Duration retryBase, Duration retryCap) {
        public Settings {
            if (batchSize < 1 || batchSize > 100 || maxAttempts < 1 || maxAttempts > 20
                    || lease == null || lease.compareTo(Duration.ofSeconds(1)) < 0
                    || retryBase == null || retryBase.compareTo(Duration.ofSeconds(1)) < 0
                    || retryCap == null || retryCap.compareTo(retryBase) < 0) {
                throw new IllegalArgumentException("Invalid notification worker settings");
            }
        }
    }

    private final NotificationOutboxClaimService claims;
    private final NotificationOutboxCompletionService completions;
    private final NotificationDeliveryPort provider;
    private final MeterRegistry metrics;
    private final Settings settings;
    private final Clock clock;

    public NotificationDeliveryWorker(NotificationOutboxClaimService claims,
                                      NotificationOutboxCompletionService completions,
                                      NotificationDeliveryPort provider,
                                      MeterRegistry metrics, Settings settings, Clock clock) {
        this.claims = claims;
        this.completions = completions;
        this.provider = provider;
        this.metrics = metrics;
        this.settings = settings;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${sentinelops.notifications.delivery.poll-delay-ms:5000}")
    public void poll() {
        dispatchBatch();
    }

    /** Returns the number of delivery attempts made in this poll. */
    public int dispatchBatch() {
        int attempts = 0;
        for (int i = 0; i < settings.batchSize(); i++) {
            var next = claims.claimNext(clock.instant(), settings.maxAttempts(), settings.lease());
            if (next.isEmpty()) break;
            var claim = next.orElseThrow();
            attempts++;
            try {
                provider.deliver(claim.payload());
                if (completions.sent(claim.id(), claim.leaseToken(), clock.instant())) {
                    metrics.counter("sentinelops.notification.delivery", "result", "sent").increment();
                } else {
                    metrics.counter("sentinelops.notification.delivery", "result", "stale").increment();
                }
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                boolean terminal = claim.attemptCount() >= settings.maxAttempts();
                var nextAttempt = clock.instant().plus(retryDelay(claim.attemptCount()));
                // Never persist arbitrary provider error bodies, URLs, tokens or secrets.
                String reason = ex instanceof NotificationDeliveryException
                        ? ex.getMessage() : ex.getClass().getSimpleName();
                if (reason == null || reason.isBlank()) reason = "Delivery failed";
                reason = reason.substring(0, Math.min(300, reason.length()));
                boolean updated = completions.failed(claim.id(), claim.leaseToken(),
                        terminal, nextAttempt, reason);
                metrics.counter("sentinelops.notification.delivery", "result",
                        updated ? (terminal ? "failed" : "retry") : "stale").increment();
                if (Thread.currentThread().isInterrupted()) break;
            }
        }
        return attempts;
    }

    private Duration retryDelay(int attempt) {
        long cap = settings.retryCap().toMillis();
        long delay = Math.min(cap, settings.retryBase().toMillis());
        for (int i = 1; i < attempt; i++) {
            delay = delay >= cap / 2 ? cap : Math.min(cap, delay * 2);
        }
        return Duration.ofMillis(delay);
    }
}
