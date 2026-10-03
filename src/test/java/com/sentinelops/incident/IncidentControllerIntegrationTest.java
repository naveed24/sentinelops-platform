package com.sentinelops.incident;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IncidentControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ServiceRepository serviceRepository;

    @Test
    void exposesCreateTransitionAndAuditHistory() throws Exception {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("incident-api-service")
                .ownerTeam("platform")
                .status(ServiceStatus.ACTIVE)
                .build());

        var createBody = Map.of(
                "title", "Checkout latency",
                "description", "p99 above SLO",
                "severity", "SEV2",
                "serviceId", service.getId()
        );

        var createResult = mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.serviceId").value(service.getId()))
                .andReturn();

        long incidentId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/v1/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACKNOWLEDGED",
                                "message", "On-call acknowledged"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mockMvc.perform(get("/api/v1/incidents/{id}/history", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("OPEN"))
                .andExpect(jsonPath("$[1].toStatus").value("ACKNOWLEDGED"));
    }

    @Test
    void returnsBadRequestForInvalidLifecycleJump() throws Exception {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("incident-api-invalid-transition")
                .ownerTeam("platform")
                .status(ServiceStatus.ACTIVE)
                .build());

        var created = mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Database saturation",
                                "severity", "SEV1",
                                "serviceId", service.getId()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        long incidentId = objectMapper.readTree(
                created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/api/v1/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RESOLVED",
                                "message", "Invalid direct resolve"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid incident transition: OPEN -> RESOLVED"));
    }
}
