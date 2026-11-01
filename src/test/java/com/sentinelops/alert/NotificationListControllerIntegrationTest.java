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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationListControllerIntegrationTest {
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
                                "teamName", "Delivery Audit", "teamSlug", "delivery-audit",
                                "email", "delivery-audit-admin@example.com",
                                "displayName", "Delivery Admin",
                                "password", "secure-delivery-audit-password"))))
                .andExpect(status().isCreated()).andReturn();
        var body = mapper.readTree(response.getResponse().getContentAsString());
        adminToken = body.get("accessToken").asText();
        teamId = body.get("user").get("teamId").asLong();
    }

    @Test
    void keysetPaginationIsStableUnderConcurrentInsertsAndDoesNotLeakSecrets() throws Exception {
        var rule = createRule();
        for (int i = 0; i < 3; i++) ingest(rule);
        List<Long> ids = outbox.findAll().stream().map(AlertNotificationOutbox::getId)
                .sorted(Comparator.reverseOrder()).toList();

        var response = mvc.perform(get("/api/v1/notifications").param("limit", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(ids.get(0)))
                .andExpect(jsonPath("$.items[1].id").value(ids.get(1)))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].ruleName").value(rule.getName()))
                .andExpect(jsonPath("$.items[0].serviceName").value(rule.getService().getName()))
                .andExpect(jsonPath("$.items[0].attemptCount").value(0))
                .andExpect(jsonPath("$.items[0].lastError").doesNotExist())
                .andExpect(jsonPath("$.items[0].leaseToken").doesNotExist())
                .andExpect(jsonPath("$.items[0].message").doesNotExist())
                .andExpect(jsonPath("$.items[0].source").doesNotExist())
                .andReturn();
        long cursor = mapper.readTree(response.getResponse().getContentAsString())
                .get("nextBeforeId").asLong();
        assertThat(cursor).isEqualTo(ids.get(1));

        // An arriving newer delivery cannot displace the remaining older page.
        ingest(rule);
        mvc.perform(get("/api/v1/notifications").param("limit", "2")
                        .param("beforeId", Long.toString(cursor))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.nextBeforeId").isEmpty());
    }

    @Test
    void filtersByStatusWithoutExposingInternalErrorDetails() throws Exception {
        var rule = createRule();
        ingest(rule);
        ingest(rule);
        var oldest = outbox.findAll().stream()
                .min(Comparator.comparing(AlertNotificationOutbox::getId)).orElseThrow();
        oldest.setStatus(AlertNotificationStatus.FAILED);
        oldest.setLastError("private webhook response body");
        outbox.saveAndFlush(oldest);

        mvc.perform(get("/api/v1/notifications").param("status", "FAILED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(oldest.getId()))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].lastError").doesNotExist());
        mvc.perform(get("/api/v1/notifications").param("status", "SENT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rejectsInvalidBoundsAndEnforcesReadOnlyViewerAccess() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        for (String limit : List.of("0", "51", "-1")) {
            mvc.perform(get("/api/v1/notifications").param("limit", limit)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/notifications").param("beforeId", "0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notifications").param("status", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "delivery-audit-viewer@example.com",
                                "displayName", "Delivery Viewer",
                                "password", "secure-delivery-viewer-password",
                                "role", "VIEWER", "teamId", teamId))))
                .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "email", "delivery-audit-viewer@example.com",
                                "password", "secure-delivery-viewer-password"))))
                .andExpect(status().isOk()).andReturn();
        String viewer = mapper.readTree(response.getResponse().getContentAsString())
                .get("accessToken").asText();
        mvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer))
                .andExpect(status().isForbidden());
    }

    private AlertRule createRule() {
        var service = services.saveAndFlush(ServiceRecord.builder()
                .name("delivery-audit-" + UUID.randomUUID()).ownerTeam("platform")
                .status(ServiceStatus.ACTIVE).build());
        String name = UUID.randomUUID().toString();
        return rules.saveAndFlush(AlertRule.builder()
                .service(service).name(name).canonicalName(name)
                .eventType("ERROR_RATE").severity(IncidentSeverity.SEV2)
                .enabled(true).cooldownSeconds(0).build());
    }

    private void ingest(AlertRule rule) {
        assertThat(ingestion.ingest("""
                {"schemaVersion":1,"eventId":"%s","serviceId":%d,
                 "source":"prometheus","type":"ERROR_RATE","message":"private event payload",
                 "occurredAt":"2026-11-01T00:00:00Z"}
                """.formatted(UUID.randomUUID(), rule.getService().getId()))).isTrue();
    }
}
