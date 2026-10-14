package com.sentinelops.incident;

import com.sentinelops.identity.TeamRepository;
import com.sentinelops.identity.UserAccountRepository;
import tools.jackson.databind.ObjectMapper;
import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
    @Autowired UserAccountRepository userAccounts;
    @Autowired TeamRepository teams;

    private String adminToken;
    private long adminUserId;
    private long adminTeamId;

    @BeforeEach
    void bootstrapAdmin() throws Exception {
        userAccounts.deleteAll();
        teams.deleteAll();

        var result = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teamName", "Incident Operations",
                                "teamSlug", "incident-operations",
                                "email", "incident-admin@example.com",
                                "displayName", "Incident Admin",
                                "password", "incident-admin-password"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        var bootstrap = objectMapper.readTree(result.getResponse().getContentAsString());
        adminToken = bootstrap.get("accessToken").asText();
        adminUserId = bootstrap.get("user").get("id").asLong();
        adminTeamId = bootstrap.get("user").get("teamId").asLong();
    }

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

        var createResult = mockMvc.perform(authorized(post("/api/v1/incidents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.serviceId").value(service.getId()))
                .andReturn();

        long incidentId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(authorized(patch("/api/v1/incidents/{id}/status", incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACKNOWLEDGED",
                                "message", "On-call acknowledged"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

        mockMvc.perform(authorized(get("/api/v1/incidents/{id}/history", incidentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toStatus").value("OPEN"))
                .andExpect(jsonPath("$[1].toStatus").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$[0].actorUserId").value(adminUserId))
                .andExpect(jsonPath("$[0].actorEmail").value("incident-admin@example.com"))
                .andExpect(jsonPath("$[1].actorUserId").value(adminUserId));
    }

    @Test
    void recordsAuthenticatedResponderSeparatelyFromIncidentCreator() throws Exception {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("incident-actor-" + System.nanoTime())
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build());

        var created = mockMvc.perform(authorized(post("/api/v1/incidents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Actor attribution regression",
                                "severity", "SEV2",
                                "serviceId", service.getId()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long incidentId = objectMapper.readTree(
                created.getResponse().getContentAsString()).get("id").asLong();

        var newUser = mockMvc.perform(authorized(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "incident-responder@example.com",
                                "displayName", "On-call Responder",
                                "password", "incident-responder-password",
                                "role", "RESPONDER",
                                "teamId", adminTeamId
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        long responderId = objectMapper.readTree(
                newUser.getResponse().getContentAsString()).get("id").asLong();

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "incident-responder@example.com",
                                "password", "incident-responder-password"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String responderToken = objectMapper.readTree(
                login.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(patch("/api/v1/incidents/{id}/status", incidentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + responderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACKNOWLEDGED",
                                "message", "Responder took ownership"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/api/v1/incidents/{id}/history", incidentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].actorUserId").value(adminUserId))
                .andExpect(jsonPath("$[0].actorEmail").value("incident-admin@example.com"))
                .andExpect(jsonPath("$[1].actorUserId").value(responderId))
                .andExpect(jsonPath("$[1].actorEmail").value("incident-responder@example.com"));
    }

    @Test
    void returnsBadRequestForInvalidLifecycleJump() throws Exception {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("incident-api-invalid-transition")
                .ownerTeam("platform")
                .status(ServiceStatus.ACTIVE)
                .build());

        var created = mockMvc.perform(authorized(post("/api/v1/incidents"))
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

        mockMvc.perform(authorized(patch("/api/v1/incidents/{id}/status", incidentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "RESOLVED",
                                "message", "Invalid direct resolve"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid incident transition: OPEN -> RESOLVED"));
    }

    @Test
    void staleVersionCannotOverwriteIncidentOrAppendAuditEvent() throws Exception {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("incident-stale-version-" + System.nanoTime())
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build());

        var created = mockMvc.perform(authorized(post("/api/v1/incidents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Concurrent status update",
                                "severity", "SEV2",
                                "serviceId", service.getId()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        var body = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = body.get("id").asLong();
        long version = body.get("version").asLong();

        mockMvc.perform(authorized(patch("/api/v1/incidents/{id}/status", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACKNOWLEDGED",
                                "message", "Operator A acknowledged",
                                "expectedVersion", version
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.version").value(version + 1));

        mockMvc.perform(authorized(patch("/api/v1/incidents/{id}/status", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "MITIGATED",
                                "message", "Stale operator B",
                                "expectedVersion", version
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Incident was modified; fetch the latest version and retry"));

        mockMvc.perform(authorized(get("/api/v1/incidents/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.version").value(version + 1));

        mockMvc.perform(authorized(get("/api/v1/incidents/{id}/history", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void negativeTransitionVersionIsRejectedByValidation() throws Exception {
        mockMvc.perform(authorized(patch("/api/v1/incidents/1/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACKNOWLEDGED",
                                "message", "Invalid precondition",
                                "expectedVersion", -1
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersIncidentsByStatusSeverityAndService() throws Exception {
        var service = serviceRepository.save(ServiceRecord.builder()
                .name("incident-filter-service")
                .ownerTeam("platform")
                .status(ServiceStatus.ACTIVE)
                .build());

        var acknowledged = mockMvc.perform(authorized(post("/api/v1/incidents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Filtered incident",
                                "severity", "SEV2",
                                "serviceId", service.getId()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(authorized(post("/api/v1/incidents"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Different severity",
                                "severity", "SEV3",
                                "serviceId", service.getId()
                        ))))
                .andExpect(status().isCreated());

        long acknowledgedId = objectMapper.readTree(
                acknowledged.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(authorized(patch("/api/v1/incidents/{id}/status", acknowledgedId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "ACKNOWLEDGED",
                                "message", "Accepted by on-call"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(authorized(get("/api/v1/incidents"))
                        .param("status", "ACKNOWLEDGED")
                        .param("severity", "SEV2")
                        .param("serviceId", service.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Filtered incident"))
                .andExpect(jsonPath("$[0].status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$[0].severity").value("SEV2"))
                .andExpect(jsonPath("$[0].serviceId").value(service.getId()));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
}
