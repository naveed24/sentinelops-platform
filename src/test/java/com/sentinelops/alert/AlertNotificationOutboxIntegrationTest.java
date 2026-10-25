package com.sentinelops.alert;

import com.sentinelops.incident.IncidentSeverity;
import com.sentinelops.ingestion.OperationalEventIngestionService;
import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AlertNotificationOutboxIntegrationTest {
    @Autowired OperationalEventIngestionService ingestion;
    @Autowired ServiceRepository services;
    @Autowired AlertRuleRepository rules;
    @Autowired AlertOccurrenceRepository occurrences;
    @Autowired AlertNotificationOutboxRepository notifications;

    @Test
    void cooldownSuppressesDeliveryIntentsButNeverLosesOccurrences() {
        var service = service();
        var rule = rule(service, "ERROR_RATE", true, 86400);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();

        assertThat(ingestion.ingest(payload(first, service.getId(), "ERROR_RATE"))).isTrue();
        assertThat(ingestion.ingest(payload(second, service.getId(), "ERROR_RATE"))).isTrue();
        assertThat(ingestion.ingest(payload(first, service.getId(), "ERROR_RATE"))).isFalse();

        assertThat(occurrences.countByEvent_EventId(first.toString())).isEqualTo(1);
        assertThat(occurrences.countByEvent_EventId(second.toString())).isEqualTo(1);
        assertThat(notifications.countByRule_Id(rule.getId())).isEqualTo(1);
        var queued = notifications.findFirstByRule_IdOrderByCreatedAtDescIdDesc(rule.getId()).orElseThrow();
        // Assert by foreign-key identity without dereferencing a detached LAZY proxy.
        var firstOccurrence = occurrences.findAll().stream()
                .filter(o -> o.getEvent().getEventId().equals(first.toString()))
                .findFirst().orElseThrow();
        assertThat(queued.getOccurrence().getId()).isEqualTo(firstOccurrence.getId());
        assertThat(queued.getStatus()).isEqualTo(AlertNotificationStatus.PENDING);
        assertThat(queued.getAttemptCount()).isZero();
        assertThat(queued.getNextAttemptAt()).isEqualTo(queued.getCreatedAt());
    }

    @Test
    void zeroCooldownSchedulesEachDistinctEventForEveryMatchingRule() {
        var service = service();
        var firstRule = rule(service, "ERROR_RATE", true, 0);
        var secondRule = rule(service, "ERROR_RATE", true, 0);
        rule(service, "LATENCY", true, 0);
        rule(service, "ERROR_RATE", false, 0);

        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        assertThat(ingestion.ingest(payload(first, service.getId(), "ERROR_RATE"))).isTrue();
        assertThat(ingestion.ingest(payload(second, service.getId(), "ERROR_RATE"))).isTrue();

        assertThat(notifications.countByRule_Id(firstRule.getId())).isEqualTo(2);
        assertThat(notifications.countByRule_Id(secondRule.getId())).isEqualTo(2);
        assertThat(occurrences.countByEvent_EventId(first.toString())).isEqualTo(2);
        assertThat(occurrences.countByEvent_EventId(second.toString())).isEqualTo(2);
    }

    @Test
    void cooldownIsPerRuleNotSharedAcrossServices() {
        var a = service();
        var b = service();
        var ruleA = rule(a, "ERROR_RATE", true, 86400);
        var ruleB = rule(b, "ERROR_RATE", true, 86400);
        assertThat(ingestion.ingest(payload(UUID.randomUUID(), a.getId(), "ERROR_RATE"))).isTrue();
        assertThat(ingestion.ingest(payload(UUID.randomUUID(), b.getId(), "ERROR_RATE"))).isTrue();
        assertThat(notifications.countByRule_Id(ruleA.getId())).isEqualTo(1);
        assertThat(notifications.countByRule_Id(ruleB.getId())).isEqualTo(1);
    }

    private ServiceRecord service() {
        return services.saveAndFlush(ServiceRecord.builder()
                .name("notification-" + UUID.randomUUID())
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build());
    }

    private AlertRule rule(ServiceRecord service, String type, boolean enabled, int cooldown) {
        String name = UUID.randomUUID().toString();
        return rules.saveAndFlush(AlertRule.builder()
                .service(service).name(name).canonicalName(name)
                .eventType(type).severity(IncidentSeverity.SEV2)
                .enabled(enabled).cooldownSeconds(cooldown).build());
    }

    private static String payload(UUID id, long serviceId, String type) {
        return """
                {"schemaVersion":1,"eventId":"%s","serviceId":%d,
                 "source":"prometheus","type":"%s","message":"threshold exceeded",
                 "occurredAt":"2026-10-24T00:00:00Z"}
                """.formatted(id, serviceId, type);
    }
}
