package com.sentinelops.incident;

import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentConcurrencyIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ServiceRepository services;

    @Test
    void staleVersionDoesNotMutateIncidentOrAudit() throws Exception {
        var service = services.save(ServiceRecord.builder()
                .name("incident-concurrency-service")
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build());
        var created = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Concurrent update", "severity", "SEV2",
                        "serviceId", service.getId()))))
                .andExpect(status().isCreated()).andReturn();
        var body = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = body.get("id").asLong();
        long version = body.get("version").asLong();

        mockMvc.perform(patch("/api/v1/incidents/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "status", "ACKNOWLEDGED", "message", "First responder",
                        "expectedVersion", version))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.version").value(version + 1));

        mockMvc.perform(patch("/api/v1/incidents/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "status", "MITIGATED", "message", "Stale responder",
                        "expectedVersion", version))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(get("/api/v1/incidents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.version").value(version + 1));
        mockMvc.perform(get("/api/v1/incidents/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].message").value("First responder"));
    }

    @Test
    void negativeExpectedVersionIsRejectedByValidation() throws Exception {
        mockMvc.perform(patch("/api/v1/incidents/{id}/status", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "status", "ACKNOWLEDGED", "message", "Invalid version",
                        "expectedVersion", -1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.expectedVersion").exists());
    }
}
