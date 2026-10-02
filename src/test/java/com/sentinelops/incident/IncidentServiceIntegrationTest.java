package com.sentinelops.incident;

import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IncidentServiceIntegrationTest {
    @Autowired IncidentService incidentService;
    @Autowired ServiceRepository serviceRepository;

    @Test
    void recordsValidLifecycleInAuditHistory() {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("payments-lifecycle").ownerTeam("platform").status(ServiceStatus.ACTIVE).build());
        var created = incidentService.create(new IncidentDtos.CreateIncidentRequest(
                "Payment latency", "p99 above SLO", IncidentSeverity.SEV2, service.getId()));

        incidentService.transition(created.id(),
                new IncidentDtos.TransitionRequest(IncidentStatus.ACKNOWLEDGED, "Acknowledged"));
        incidentService.transition(created.id(),
                new IncidentDtos.TransitionRequest(IncidentStatus.MITIGATED, "Mitigated"));
        var resolved = incidentService.transition(created.id(),
                new IncidentDtos.TransitionRequest(IncidentStatus.RESOLVED, "Recovered"));

        assertThat(resolved.resolvedAt()).isNotNull();
        assertThat(incidentService.history(created.id()))
                .extracting(IncidentDtos.AuditResponse::toStatus)
                .containsExactly(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED,
                        IncidentStatus.MITIGATED, IncidentStatus.RESOLVED);
    }

    @Test
    void rejectsInvalidTransition() {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("database-lifecycle").ownerTeam("platform").status(ServiceStatus.ACTIVE).build());
        var created = incidentService.create(new IncidentDtos.CreateIncidentRequest(
                "Database errors", null, IncidentSeverity.SEV1, service.getId()));

        assertThatThrownBy(() -> incidentService.transition(created.id(),
                new IncidentDtos.TransitionRequest(IncidentStatus.RESOLVED, "Invalid jump")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(incidentService.history(created.id())).hasSize(1);
    }
}
