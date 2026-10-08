package com.sentinelops.incident;

import com.sentinelops.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentPaginationIntegrationTest {
    @Autowired IncidentService incidents;
    @Autowired ServiceRepository services;
    @Autowired MockMvc mvc;

    @Test
    void returnsFilteredPagesInNewestFirstOrder() throws Exception {
        var service = services.save(ServiceRecord.builder()
                .name("incident-pagination-service").ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        long a = create(service.getId(), "first").id();
        long b = create(service.getId(), "second").id();
        long c = create(service.getId(), "third").id();

        mvc.perform(get("/api/v1/incidents").param("serviceId", service.getId().toString())
                        .param("severity", "SEV2").param("limit", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(c))
                .andExpect(jsonPath("$[1].id").value(b));

        mvc.perform(get("/api/v1/incidents").param("serviceId", service.getId().toString())
                        .param("severity", "SEV2").param("limit", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(a));
    }

    @Test
    void rejectsOutOfRangePageAndLimit() throws Exception {
        mvc.perform(get("/api/v1/incidents").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/incidents").param("page", "1001"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/incidents").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/incidents").param("limit", "201"))
                .andExpect(status().isBadRequest());
    }

    private IncidentDtos.IncidentResponse create(Long id, String title) {
        return incidents.create(new IncidentDtos.CreateIncidentRequest(title, null,
                IncidentSeverity.SEV2, id));
    }
}
