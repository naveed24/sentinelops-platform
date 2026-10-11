package com.sentinelops.incident;

import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class IncidentAuditOrderingIntegrationTest {
    @Autowired IncidentService incidentService;
    @Autowired IncidentAuditEventRepository audit;
    @Autowired ServiceRepository services;

    @Test
    void ordersEqualTimestampEventsById() {
        var service = services.save(ServiceRecord.builder()
                .name("audit-order-" + System.nanoTime())
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build());
        long incidentId = incidentService.create(new IncidentDtos.CreateIncidentRequest(
                "Ordering regression", null, IncidentSeverity.SEV3, service.getId())).id();
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        var first = audit.save(IncidentAuditEvent.builder().incidentId(incidentId)
                .toStatus(IncidentStatus.ACKNOWLEDGED).message("first")
                .createdAt(timestamp).build());
        var second = audit.save(IncidentAuditEvent.builder().incidentId(incidentId)
                .toStatus(IncidentStatus.MITIGATED).message("second")
                .createdAt(timestamp).build());

        var ids = audit.findByIncidentIdOrderByCreatedAtAsc(incidentId).stream()
                .filter(event -> timestamp.equals(event.getCreatedAt()))
                .map(IncidentAuditEvent::getId).toList();
        assertThat(ids).containsExactly(first.getId(), second.getId());
    }
}
