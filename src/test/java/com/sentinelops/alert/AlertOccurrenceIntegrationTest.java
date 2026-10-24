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
class AlertOccurrenceIntegrationTest {
    @Autowired OperationalEventIngestionService ingestion;
    @Autowired AlertRuleRepository rules;
    @Autowired AlertOccurrenceRepository occurrences;
    @Autowired ServiceRepository services;

    @Test
    void matchesOnlyEnabledRulesForServiceAndTypeAndIgnoresReplay() {
        var primary = service("primary");
        var other = service("other");
        var matching = rule(primary, "ERROR_RATE", true, IncidentSeverity.SEV2);
        rule(primary, "LATENCY", true, IncidentSeverity.SEV1);
        rule(primary, "ERROR_RATE", false, IncidentSeverity.SEV1);
        rule(other, "ERROR_RATE", true, IncidentSeverity.SEV1);
        UUID eventId = UUID.randomUUID();

        assertThat(ingestion.ingest(payload(eventId, primary.getId(), "ERROR_RATE"))).isTrue();
        assertThat(ingestion.ingest(payload(eventId, primary.getId(), "ERROR_RATE"))).isFalse();
        assertThat(occurrences.countByEvent_EventId(eventId.toString())).isEqualTo(1);
        assertThat(occurrences.existsByRule_IdAndEvent_EventId(
                matching.getId(), eventId.toString())).isTrue();
        assertThat(occurrences.findAll().stream()
                .filter(o -> o.getEvent().getEventId().equals(eventId.toString()))
                .findFirst().orElseThrow().getSeverity()).isEqualTo(IncidentSeverity.SEV2);
    }

    @Test
    void separateEventsGenerateSeparateOccurrences() {
        var service = service("repeated");
        rule(service, "ERROR_RATE", true, IncidentSeverity.SEV3);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        assertThat(ingestion.ingest(payload(first, service.getId(), "ERROR_RATE"))).isTrue();
        assertThat(ingestion.ingest(payload(second, service.getId(), "ERROR_RATE"))).isTrue();
        assertThat(occurrences.countByEvent_EventId(first.toString())).isEqualTo(1);
        assertThat(occurrences.countByEvent_EventId(second.toString())).isEqualTo(1);
    }

    private ServiceRecord service(String suffix) {
        return services.saveAndFlush(ServiceRecord.builder()
                .name("alert-eval-" + suffix + "-" + UUID.randomUUID())
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build());
    }

    private AlertRule rule(ServiceRecord service, String type, boolean enabled, IncidentSeverity severity) {
        String name = UUID.randomUUID().toString();
        return rules.saveAndFlush(AlertRule.builder()
                .service(service).name(name).canonicalName(name)
                .eventType(type).severity(severity)
                .enabled(enabled).cooldownSeconds(60).build());
    }

    private static String payload(UUID id, long serviceId, String type) {
        return """
                {"schemaVersion":1,"eventId":"%s","serviceId":%d,
                 "source":"prometheus","type":"%s","message":"threshold exceeded",
                 "occurredAt":"2026-10-24T00:00:00Z"}
                """.formatted(id, serviceId, type);
    }
}
