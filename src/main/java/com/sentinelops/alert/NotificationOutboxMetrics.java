package com.sentinelops.alert;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodically samples durable outbox health into low-cardinality Prometheus
 * gauges. Scrapes read in-memory values, never issue database queries.
 * Failed refreshes retain the last good snapshot and expose its age.
 */
@Slf4j
@Component
public class NotificationOutboxMetrics {
    private final AlertNotificationOutboxRepository outbox;
    private final MeterRegistry registry;
    private final Clock clock;
    private final EnumMap<AlertNotificationStatus, AtomicLong> depths =
            new EnumMap<>(AlertNotificationStatus.class);
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();
    private final AtomicLong lastSuccessfulSampleEpochSeconds = new AtomicLong();

    public NotificationOutboxMetrics(AlertNotificationOutboxRepository outbox,
                                     MeterRegistry registry) {
        this(outbox, registry, Clock.systemUTC());
    }

    // Package-private seam for deterministic tests.
    NotificationOutboxMetrics(AlertNotificationOutboxRepository outbox,
                              MeterRegistry registry, Clock clock) {
        this.outbox = outbox;
        this.registry = registry;
        this.clock = clock;
        for (AlertNotificationStatus status : AlertNotificationStatus.values()) {
            var value = new AtomicLong();
            depths.put(status, value);
            Gauge.builder("sentinelops.notification.outbox.depth", value, AtomicLong::get)
                    .description("Durable notification rows by delivery state")
                    .tag("status", status.name().toLowerCase(Locale.ROOT))
                    .register(registry);
        }
        Gauge.builder("sentinelops.notification.outbox.oldest.pending.age",
                        oldestPendingAgeSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .description("Age of the oldest pending notification, zero when none")
                .register(registry);
        Gauge.builder("sentinelops.notification.outbox.last.successful.sample",
                        lastSuccessfulSampleEpochSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .description("Unix epoch of the most recent successful outbox metric sample")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${sentinelops.notifications.monitor.poll-delay-ms:30000}",
               initialDelayString = "${sentinelops.notifications.monitor.initial-delay-ms:30000}")
    public void refresh() {
        try {
            // Stage both database results before publishing any values. A partial
            // read failure must not turn the previous snapshot into false zeros.
            var counts = new EnumMap<AlertNotificationStatus, Long>(AlertNotificationStatus.class);
            for (AlertNotificationStatus status : AlertNotificationStatus.values()) {
                counts.put(status, 0L);
            }
            for (Object[] row : outbox.countGroupedByStatus()) {
                counts.put((AlertNotificationStatus) row[0], (Long) row[1]);
            }
            Instant oldest = outbox.oldestCreatedAtForStatus(AlertNotificationStatus.PENDING);
            Instant now = clock.instant();
            long age = oldest == null ? 0 : Math.max(0, Duration.between(oldest, now).getSeconds());
            for (AlertNotificationStatus status : AlertNotificationStatus.values()) {
                depths.get(status).set(counts.get(status));
            }
            oldestPendingAgeSeconds.set(age);
            lastSuccessfulSampleEpochSeconds.set(now.getEpochSecond());
            registry.counter("sentinelops.notification.outbox.sample", "result", "success").increment();
        } catch (RuntimeException ex) {
            registry.counter("sentinelops.notification.outbox.sample", "result", "error").increment();
            // Do not log query arguments, notification payloads, URLs or secrets.
            log.warn("Notification outbox metrics sample failed: {}", ex.getClass().getSimpleName());
        }
    }
}
