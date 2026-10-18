package com.sentinelops.incident;

import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IncidentLifecycleMetricsIntegrationTest {
    @Autowired IncidentService incidents;
    @Autowired ServiceRepository services;
    @Autowired MeterRegistry registry;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void countsCommittedCreationsAndTransitionsButNotRejectedTransitions() {
        var service = createService();
        double beforeCreate = count("created", "SEV3", "NONE", "OPEN");
        double beforeAcknowledge = count("transition", "SEV3", "OPEN", "ACKNOWLEDGED");
        double beforeRejected = count("transition", "SEV3", "OPEN", "RESOLVED");

        var incident = incidents.create(new IncidentDtos.CreateIncidentRequest(
                "High request latency", null, IncidentSeverity.SEV3, service.getId()));
        assertThat(count("created", "SEV3", "NONE", "OPEN")).isEqualTo(beforeCreate + 1);

        assertThatThrownBy(() -> incidents.transition(incident.id(),
                new IncidentDtos.TransitionRequest(IncidentStatus.RESOLVED, "Invalid jump")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(count("transition", "SEV3", "OPEN", "RESOLVED")).isEqualTo(beforeRejected);

        incidents.transition(incident.id(),
                new IncidentDtos.TransitionRequest(IncidentStatus.ACKNOWLEDGED, "On call acknowledged"));
        assertThat(count("transition", "SEV3", "OPEN", "ACKNOWLEDGED"))
                .isEqualTo(beforeAcknowledge + 1);
    }

    @Test
    void doesNotCountRolledBackCreation() {
        var service = createService();
        double before = count("created", "SEV4", "NONE", "OPEN");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            incidents.create(new IncidentDtos.CreateIncidentRequest(
                    "Rolled back incident", null, IncidentSeverity.SEV4, service.getId()));
            status.setRollbackOnly();
        });

        assertThat(count("created", "SEV4", "NONE", "OPEN")).isEqualTo(before);
    }

    private ServiceRecord createService() {
        return services.save(ServiceRecord.builder()
                .name("incident-metrics-" + UUID.randomUUID())
                .ownerTeam("platform")
                .status(ServiceStatus.ACTIVE)
                .build());
    }

    private double count(String operation, String severity, String from, String to) {
        var counter = registry.find("sentinelops.incident.lifecycle")
                .tags("operation", operation, "severity", severity, "from", from, "to", to)
                .counter();
        return counter == null ? 0 : counter.count();
    }
}
