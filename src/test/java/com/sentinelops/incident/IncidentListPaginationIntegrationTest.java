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
class IncidentListPaginationIntegrationTest {
    @Autowired IncidentService incidents;
    @Autowired ServiceRepository services;

    @Test
    void filtersBeforePaginatingAndKeepsNewestFirst() {
        var service = services.save(ServiceRecord.builder()
                .name("incident-page-" + System.nanoTime())
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build());

        long[] ids = new long[5];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = incidents.create(new IncidentDtos.CreateIncidentRequest(
                    "Incident " + i, null, IncidentSeverity.SEV2, service.getId())).id();
        }

        for (int page = 0; page < 3; page++) {
            var results = incidents.list(null, IncidentSeverity.SEV2, service.getId(), page, 2);
            int expected = page == 2 ? 1 : 2;
            assertThat(results).hasSize(expected);
            for (int i = 0; i < expected; i++) {
                assertThat(results.get(i).id()).isEqualTo(ids[4 - page * 2 - i]);
            }
        }

        assertThat(incidents.list(null, null, service.getId(), 3, 2)).isEmpty();
        assertThat(incidents.list(null, IncidentSeverity.SEV1, service.getId(), 0, 2)).isEmpty();
    }

    @Test
    void rejectsInvalidPagesLimitsAndOffsetOverflow() {
        assertThatThrownBy(() -> incidents.list(null, null, null, -1, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> incidents.list(null, null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> incidents.list(null, null, null, 0, 201))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> incidents.list(null, null, null, Integer.MAX_VALUE, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page offset is too large");
    }
}
