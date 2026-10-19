package com.sentinelops.incident;

import com.sentinelops.common.NotFoundException;
import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IncidentHistoryCursorIntegrationTest {
    @Autowired IncidentHistoryCursorService history;
    @Autowired IncidentService incidents;
    @Autowired IncidentAuditEventRepository audit;
    @Autowired ServiceRepository services;

    @Test
    void pagesWithoutDuplicatesAcrossEqualTimestampsAndOtherIncidents() {
        long incidentId = createIncident();
        long otherIncidentId = createIncident();
        long creationEventId = history.page(incidentId, null, null, 10).get(0).id();
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

        long first = addEvent(incidentId, timestamp, "first");
        addEvent(otherIncidentId, timestamp, "unrelated");
        long second = addEvent(incidentId, timestamp, "second");
        long third = addEvent(incidentId, timestamp.plusSeconds(1), "third");

        var page1 = history.page(incidentId, null, null, 2);
        assertThat(page1).extracting(IncidentDtos.AuditResponse::id)
                .containsExactly(first, second);

        var page2 = history.page(incidentId, page1.get(1).createdAt(), page1.get(1).id(), 2);
        assertThat(page2).extracting(IncidentDtos.AuditResponse::id)
                .containsExactly(third, creationEventId);

        var page3 = history.page(incidentId, page2.get(1).createdAt(), page2.get(1).id(), 2);
        assertThat(page3).isEmpty();
    }

    @Test
    void validatesCursorPairsBoundsAndIncidentExistence() {
        long id = createIncident();

        assertThatThrownBy(() -> history.page(id, null, 10L, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
        assertThatThrownBy(() -> history.page(id, Instant.now(), null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provided together");
        assertThatThrownBy(() -> history.page(id, Instant.now(), 0L, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("afterId must be positive");
        assertThatThrownBy(() -> history.page(id, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.page(id, null, null, 201))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.page(Long.MAX_VALUE, null, null, 10))
                .isInstanceOf(NotFoundException.class);
    }

    private long createIncident() {
        var service = services.save(ServiceRecord.builder()
                .name("cursor-" + UUID.randomUUID())
                .ownerTeam("platform")
                .status(ServiceStatus.ACTIVE)
                .build());
        return incidents.create(new IncidentDtos.CreateIncidentRequest(
                "Cursor pagination regression", null, IncidentSeverity.SEV3, service.getId())).id();
    }

    private long addEvent(long incidentId, Instant createdAt, String message) {
        return audit.save(IncidentAuditEvent.builder()
                .incidentId(incidentId)
                .toStatus(IncidentStatus.ACKNOWLEDGED)
                .message(message)
                .createdAt(createdAt)
                .build()).getId();
    }
}
