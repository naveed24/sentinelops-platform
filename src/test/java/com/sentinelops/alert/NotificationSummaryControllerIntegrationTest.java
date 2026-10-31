package com.sentinelops.alert;

import com.sentinelops.identity.TeamRepository;
import com.sentinelops.identity.UserAccountRepository;
import com.sentinelops.incident.IncidentSeverity;
import com.sentinelops.ingestion.OperationalEventIngestionService;
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
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationSummaryControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserAccountRepository users;
    @Autowired TeamRepository teams;
    @Autowired AlertNotificationOutboxRepository outbox;
    @Autowired AlertRuleRepository rules;
    @Autowired ServiceRepository services;
    @Autowired OperationalEventIngestionService ingestion;

    private String adminToken;
    private long teamId;

    @BeforeEach
    void prepare() throws Exception {
        outbox.deleteAllInBatch();
        users.deleteAll();
        teams.deleteAll();
        var response = mvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "teamName", "Notification Ops", "teamSlug", "notification-ops",
                                "email", "notification-admin@example.com",
                                "displayName", "Notification Admin",
                                "password", "secure-notification-admin-password"))))
                .andExpect(status().isCreated()).andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        adminToken = body.get("accessToken").asText();
        teamId = body.get("user").get("teamId").asLong();
    }

    @Test
    void returnsOnlyBoundedDeliveryAggregatesAndAge() throws Exception {
        var service = services.saveAndFlush(ServiceRecord.builder()
                .name("notification-summary-" + UUID.randomUUID()).ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        String name = UUID.randomUUID().toString();
        var rule = rules.saveAndFlush(AlertRule.builder()
                .service(service).name(name).canonicalName(name)
                .eventType("ERROR_RATE").severity(IncidentSeverity.SEV2)
                .enabled(true).cooldownSeconds(0).build());
        ingestion.ingest("""
                {"schemaVersion":1,"eventId":"%s","serviceId":%d,
                 "source":"prometheus","type":"ERROR_RATE","message":"threshold exceeded",
                 "occurredAt":"2026-10-31T00:00:00Z"}
                """.formatted(UUID.randomUUID(), service.getId()));
        var notification = outbox.findFirstByRule_IdOrderByCreatedAtDescIdDesc(rule.getId()).orElseThrow();

        mvc.perform(get("/api/v1/notifications/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.sending").value(0))
                .andExpect(jsonPath("$.sent").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.oldestPendingAgeSeconds").isNumber())
                .andExpect(jsonPath("$.sampledAt").exists())
                .andExpect(jsonPath("$.lastError").doesNotExist())
                .andExpect(jsonPath("$.leaseToken").doesNotExist());

        notification.setStatus(AlertNotificationStatus.FAILED);
        outbox.saveAndFlush(notification);
        mvc.perform(get("/api/v1/notifications/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(0))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.oldestPendingAgeSeconds").value(0));
    }

    @Test
    void enforcesAuthenticationAndReadOnlyViewerPermissions() throws Exception {
        mvc.perform(get("/api/v1/notifications/summary")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "notification-viewer@example.com",
                                "displayName", "Notification Viewer",
                                "password", "secure-notification-viewer-password",
                                "role", "VIEWER", "teamId", teamId))))
                .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "notification-viewer@example.com",
                                "password", "secure-notification-viewer-password"))))
                .andExpect(status().isOk()).andReturn();
        String viewerToken = mapper.readTree(response.getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(get("/api/v1/notifications/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/notifications/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isForbidden());
    }
}
