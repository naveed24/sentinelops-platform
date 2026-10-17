package com.sentinelops.incident;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IncidentTransitionPolicyTest {
    @Test
    void onlyForwardLifecycleTransitionsAreAllowed() {
        for (IncidentStatus from : IncidentStatus.values()) {
            for (IncidentStatus to : IncidentStatus.values()) {
                boolean expected = switch (from) {
                    case OPEN -> to == IncidentStatus.ACKNOWLEDGED || to == IncidentStatus.MITIGATED;
                    case ACKNOWLEDGED -> to == IncidentStatus.MITIGATED;
                    case MITIGATED -> to == IncidentStatus.RESOLVED;
                    case RESOLVED -> false;
                };
                assertThat(IncidentService.allowed(from, to))
                        .as("transition %s -> %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }
}
