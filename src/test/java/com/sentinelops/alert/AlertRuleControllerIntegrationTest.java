package com.sentinelops.alert;

import com.sentinelops.identity.TeamRepository;
import com.sentinelops.identity.UserAccountRepository;
import com.sentinelops.service.ServiceRecord;
import com.sentinelops.service.ServiceRepository;
import com.sentinelops.service.ServiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlertRuleControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired ServiceRepository services;
    @Autowired UserAccountRepository users;
    @Autowired TeamRepository teams;

    private String adminToken;
    private long teamId;

    @BeforeEach
    void bootstrapAdmin() throws Exception {
        users.deleteAll();
        teams.deleteAll();
        var response = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "teamName", "Alert Operations",
                                "teamSlug", "alert-operations",
                                "email", "alert-admin@example.com",
                                "displayName", "Alert Admin",
                                "password", "secure-alert-admin-password"))))
                .andExpect(status().isCreated()).andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        adminToken = body.get("accessToken").asText();
        teamId = body.get("user").get("teamId").asLong();
    }

    @Test
    void createsListsAndUpdatesWithOptimisticVersion() throws Exception {
        long serviceId = createService();
        var created = mockMvc.perform(admin(post("/api/v1/alert-rules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createBody(serviceId, "Checkout errors"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Checkout errors"))
                .andExpect(jsonPath("$.eventType").value("ERROR_RATE"))
                .andExpect(jsonPath("$.severity").value("SEV2"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.cooldownSeconds").value(300))
                .andReturn();
        var body = mapper.readTree(created.getResponse().getContentAsString());
        long id = body.get("id").asLong();
        long version = body.get("version").asLong();

        mockMvc.perform(admin(get("/api/v1/alert-rules/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(serviceId));
        mockMvc.perform(admin(get("/api/v1/alert-rules").param("serviceId", "" + serviceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id));

        var update = Map.of(
                "name", "Checkout errors - paging",
                "eventType", "LATENCY_SPIKE",
                "severity", "SEV1",
                "enabled", false,
                "cooldownSeconds", 900,
                "expectedVersion", version);
        mockMvc.perform(admin(put("/api/v1/alert-rules/{id}", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(version + 1))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.eventType").value("LATENCY_SPIKE"));

        mockMvc.perform(admin(put("/api/v1/alert-rules/{id}", id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(update)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Alert rule was modified; fetch the latest version and retry"));
        mockMvc.perform(admin(get("/api/v1/alert-rules/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Checkout errors - paging"))
                .andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void validatesInputsRejectsUnknownServicesAndDuplicateNames() throws Exception {
        long serviceId = createService();
        mockMvc.perform(admin(post("/api/v1/alert-rules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createBody(serviceId, "  Checkout Errors  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Checkout Errors"));

        mockMvc.perform(admin(post("/api/v1/alert-rules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createBody(serviceId, "checkout errors"))))
                .andExpect(status().isConflict());

        mockMvc.perform(admin(post("/api/v1/alert-rules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createBody(9999999L, "Unknown"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(admin(post("/api/v1/alert-rules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "serviceId", serviceId, "name", "Invalid event",
                                "eventType", "invalid event", "severity", "SEV2",
                                "enabled", true, "cooldownSeconds", 90000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.eventType").exists())
                .andExpect(jsonPath("$.fieldErrors.cooldownSeconds").exists());

        for (String[] invalid : new String[][]{{"-1", "10"}, {"0", "0"}, {"0", "101"}}) {
            mockMvc.perform(admin(get("/api/v1/alert-rules"))
                            .param("page", invalid[0]).param("size", invalid[1]))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(admin(get("/api/v1/alert-rules/9999999")))
                .andExpect(status().isNotFound());
    }

    @Test
    void allowsViewerReadsButOnlyAdminMayChangeRules() throws Exception {
        long serviceId = createService();
        var created = mockMvc.perform(admin(post("/api/v1/alert-rules"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createBody(serviceId, "Read only rule"))))
                .andExpect(status().isCreated()).andReturn();
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(admin(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "alert-viewer@example.com",
                                "displayName", "Alert Viewer",
                                "password", "secure-alert-viewer-password",
                                "role", "VIEWER", "teamId", teamId))))
                .andExpect(status().isCreated());
        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "alert-viewer@example.com",
                                "password", "secure-alert-viewer-password"))))
                .andExpect(status().isOk()).andReturn();
        String viewerToken = mapper.readTree(login.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/alert-rules/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/alert-rules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(createBody(serviceId, "Denied"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/alert-rules/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "name", "Denied", "eventType", "ERROR_RATE", "severity", "SEV1",
                                "enabled", false, "cooldownSeconds", 10, "expectedVersion", 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/alert-rules/{id}", id))
                .andExpect(status().isUnauthorized());
    }

    private long createService() {
        return services.saveAndFlush(ServiceRecord.builder()
                .name("alert-service-" + UUID.randomUUID())
                .ownerTeam("platform").status(ServiceStatus.ACTIVE).build()).getId();
    }

    private Map<String, Object> createBody(long serviceId, String name) {
        return Map.of("serviceId", serviceId, "name", name, "eventType", "ERROR_RATE",
                "severity", "SEV2", "enabled", true, "cooldownSeconds", 300);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
}
