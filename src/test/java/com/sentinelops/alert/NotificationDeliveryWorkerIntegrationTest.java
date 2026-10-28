package com.sentinelops.alert;

import com.sentinelops.incident.IncidentSeverity;
import com.sentinelops.ingestion.OperationalEventIngestionService;
import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationDeliveryWorkerIntegrationTest {
    private static final Instant START = Instant.parse("2035-01-01T00:00:00Z");

    @Autowired OperationalEventIngestionService ingestion;
    @Autowired ServiceRepository services;
    @Autowired AlertRuleRepository rules;
    @Autowired AlertNotificationOutboxRepository outbox;
    @Autowired NotificationOutboxClaimService claims;
    @Autowired NotificationOutboxCompletionService completions;

    @BeforeEach
    void clearQueuedNotifications() {
        // Other integration test classes use the same cached in-memory context.
        outbox.deleteAllInBatch();
    }

    @Test
    void successfulWebhookMarksSentAndCarriesStableIdentity() {
        var queued = queueNotification();
        var received = new ArrayList<NotificationDeliveryPayload>();
        var worker = worker(START, 3, received::add);

        assertThat(worker.dispatchBatch()).isEqualTo(1);
        assertThat(received).hasSize(1);
        assertThat(received.getFirst().deliveryId()).isEqualTo(queued.getId());
        assertThat(received.getFirst().eventType()).isEqualTo("ERROR_RATE");
        var saved = outbox.findById(queued.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AlertNotificationStatus.SENT);
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getDeliveredAt()).isEqualTo(START);
        assertThat(saved.getLeaseToken()).isNull();
        assertThat(worker.dispatchBatch()).isZero();
    }

    @Test
    void transientFailureRetriesAfterBackoffAndThenSucceeds() {
        var queued = queueNotification();
        var failing = worker(START, 3, payload -> {
            throw new NotificationDeliveryException("Webhook HTTP 503");
        });
        assertThat(failing.dispatchBatch()).isEqualTo(1);
        var retry = outbox.findById(queued.getId()).orElseThrow();
        assertThat(retry.getStatus()).isEqualTo(AlertNotificationStatus.PENDING);
        assertThat(retry.getAttemptCount()).isEqualTo(1);
        assertThat(retry.getNextAttemptAt()).isEqualTo(START.plusSeconds(30));
        assertThat(retry.getLastError()).isEqualTo("Webhook HTTP 503");
        assertThat(failing.dispatchBatch()).isZero();

        var received = new ArrayList<NotificationDeliveryPayload>();
        assertThat(worker(START.plusSeconds(31), 3, received::add).dispatchBatch()).isEqualTo(1);
        assertThat(received).hasSize(1);
        var sent = outbox.findById(queued.getId()).orElseThrow();
        assertThat(sent.getStatus()).isEqualTo(AlertNotificationStatus.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(2);
        assertThat(sent.getLastError()).isNull();
    }

    @Test
    void exhaustedAttemptsAreTerminalAndNeverResent() {
        var queued = queueNotification();
        NotificationDeliveryPort failing = payload -> {
            throw new NotificationDeliveryException("Webhook HTTP 500");
        };
        assertThat(worker(START, 2, failing).dispatchBatch()).isEqualTo(1);
        assertThat(worker(START.plusSeconds(31), 2, failing).dispatchBatch()).isEqualTo(1);
        var terminal = outbox.findById(queued.getId()).orElseThrow();
        assertThat(terminal.getStatus()).isEqualTo(AlertNotificationStatus.FAILED);
        assertThat(terminal.getAttemptCount()).isEqualTo(2);
        assertThat(worker(START.plusSeconds(1000), 2, failing).dispatchBatch()).isZero();
    }

    @Test
    void expiredLeaseCanBeReclaimedAndStaleWorkerCannotAcknowledge() {
        var queued = queueNotification();
        var first = claims.claimNext(START, 3, Duration.ofSeconds(60)).orElseThrow();
        assertThat(claims.claimNext(START, 3, Duration.ofSeconds(60))).isEmpty();
        var second = claims.claimNext(START.plusSeconds(61), 3, Duration.ofSeconds(60)).orElseThrow();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.leaseToken()).isNotEqualTo(first.leaseToken());
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(completions.sent(first.id(), first.leaseToken(), START.plusSeconds(62))).isFalse();
        assertThat(completions.sent(second.id(), second.leaseToken(), START.plusSeconds(62))).isTrue();
        assertThat(outbox.findById(queued.getId()).orElseThrow().getStatus())
                .isEqualTo(AlertNotificationStatus.SENT);
    }

    @Test
    void crashOnLastAttemptEventuallyTransitionsToFailed() {
        var queued = queueNotification();
        assertThat(claims.claimNext(START, 1, Duration.ofSeconds(60))).isPresent();
        assertThat(claims.claimNext(START.plusSeconds(61), 1, Duration.ofSeconds(60))).isEmpty();
        var saved = outbox.findById(queued.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AlertNotificationStatus.FAILED);
        assertThat(saved.getLeaseToken()).isNull();
    }

    private NotificationDeliveryWorker worker(Instant now, int maxAttempts, NotificationDeliveryPort port) {
        var settings = new NotificationDeliveryWorker.Settings(10, maxAttempts,
                Duration.ofSeconds(60), Duration.ofSeconds(30), Duration.ofMinutes(10));
        return new NotificationDeliveryWorker(claims, completions, port,
                new SimpleMeterRegistry(), ObservationRegistry.create(),
                settings, Clock.fixed(now, ZoneOffset.UTC));
    }

    private AlertNotificationOutbox queueNotification() {
        var service = services.saveAndFlush(ServiceRecord.builder()
                .name("delivery-" + UUID.randomUUID()).ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        String name = UUID.randomUUID().toString();
        var rule = rules.saveAndFlush(AlertRule.builder()
                .service(service).name(name).canonicalName(name)
                .eventType("ERROR_RATE").severity(IncidentSeverity.SEV2)
                .enabled(true).cooldownSeconds(60).build());
        var id = UUID.randomUUID();
        assertThat(ingestion.ingest("""
                {"schemaVersion":1,"eventId":"%s","serviceId":%d,
                 "source":"prometheus","type":"ERROR_RATE","message":"threshold exceeded",
                 "occurredAt":"2026-10-26T00:00:00Z"}
                """.formatted(id, service.getId()))).isTrue();
        return outbox.findFirstByRule_IdOrderByCreatedAtDescIdDesc(rule.getId()).orElseThrow();
    }
}
