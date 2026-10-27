package com.sentinelops.alert;

import com.sentinelops.incident.IncidentSeverity;
import com.sentinelops.ingestion.OperationalEventIngestionService;
import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationOutboxMetricsIntegrationTest {
    @Autowired NotificationOutboxMetrics sampler;
    @Autowired AlertNotificationOutboxRepository outbox;
    @Autowired AlertRuleRepository rules;
    @Autowired ServiceRepository services;
    @Autowired OperationalEventIngestionService ingestion;
    @Autowired MeterRegistry meters;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearOutbox() {
        outbox.deleteAllInBatch();
        sampler.refresh();
    }

    @Test
    void samplesBacklogByStatusWithoutRequiringDeliveryWorker() {
        var notification = queueNotification();
        sampler.refresh();
        assertThat(depth("pending")).isEqualTo(1);
        assertThat(depth("sending")).isZero();
        assertThat(depth("sent")).isZero();
        assertThat(depth("failed")).isZero();
        assertThat(meters.get("sentinelops.notification.outbox.last.successful.sample")
                .gauge().value()).isPositive();

        notification.setStatus(AlertNotificationStatus.SENT);
        outbox.saveAndFlush(notification);
        sampler.refresh();
        assertThat(depth("pending")).isZero();
        assertThat(depth("sent")).isEqualTo(1);
        assertThat(meters.get("sentinelops.notification.outbox.oldest.pending.age")
                .gauge().value()).isZero();
    }

    @Test
    void reportsOldestPendingAgeAndResetsWhenQueueIsEmpty() {
        var notification = queueNotification();
        jdbc.update("update alert_notification_outbox set created_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(125)), notification.getId());
        sampler.refresh();
        assertThat(meters.get("sentinelops.notification.outbox.oldest.pending.age")
                .gauge().value()).isGreaterThanOrEqualTo(120);

        outbox.deleteAllInBatch();
        sampler.refresh();
        assertThat(depth("pending")).isZero();
        assertThat(meters.get("sentinelops.notification.outbox.oldest.pending.age")
                .gauge().value()).isZero();
    }

    private double depth(String status) {
        return meters.get("sentinelops.notification.outbox.depth")
                .tag("status", status).gauge().value();
    }

    private AlertNotificationOutbox queueNotification() {
        var service = services.saveAndFlush(ServiceRecord.builder()
                .name("outbox-metrics-" + UUID.randomUUID()).ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        String name = UUID.randomUUID().toString();
        var rule = rules.saveAndFlush(AlertRule.builder()
                .service(service).name(name).canonicalName(name)
                .eventType("ERROR_RATE").severity(IncidentSeverity.SEV2)
                .enabled(true).cooldownSeconds(60).build());
        assertThat(ingestion.ingest("""
                {"schemaVersion":1,"eventId":"%s","serviceId":%d,
                 "source":"prometheus","type":"ERROR_RATE","message":"threshold exceeded",
                 "occurredAt":"2026-10-27T00:00:00Z"}
                """.formatted(UUID.randomUUID(), service.getId()))).isTrue();
        return outbox.findFirstByRule_IdOrderByCreatedAtDescIdDesc(rule.getId()).orElseThrow();
    }
}
