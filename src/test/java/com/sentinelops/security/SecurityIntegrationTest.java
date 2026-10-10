package com.sentinelops.security;

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
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;
    @Autowired TeamRepository teams;
    @Autowired ServiceRepository services;

    private String adminToken;
    private long teamId;

    @BeforeEach
    void bootstrapAdmin() throws Exception {
        users.deleteAll();
        teams.deleteAll();

        var result = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teamName", "Security Operations",
                                "teamSlug", "security-operations",
                                "email", "security-admin@example.com",
                                "displayName", "Security Admin",
                                "password", "security-admin-password"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        adminToken = body.get("accessToken").asText();
        teamId = body.get("user").get("teamId").asLong();
    }

    @Test
    void requiresAuthenticationAndEnforcesIncidentRoles() throws Exception {
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized());

        String viewerToken = createUserAndLogin(
                "viewer@example.com", "VIEWER", "viewer-secure-password");

        mockMvc.perform(get("/api/v1/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Viewer cannot create",
                                "severity", "SEV3",
                                "serviceId", 1
                        ))))
                .andExpect(status().isForbidden());

        String responderToken = createUserAndLogin(
                "responder@example.com", "RESPONDER", "responder-secure-password");

        var service = services.save(ServiceRecord.builder()
                .name("security-rbac-service")
                .ownerTeam("security")
                .status(ServiceStatus.ACTIVE)
                .build());

        mockMvc.perform(post("/api/v1/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + responderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Responder can create",
                                "severity", "SEV2",
                                "serviceId", service.getId()
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsBadCredentialsAndImmediatelyInvalidatesDisabledUserTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "security-admin@example.com",
                                "password", "wrong-password"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        var created = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "disable-me@example.com",
                                "displayName", "Disable Me",
                                "password", "disable-me-password",
                                "role", "VIEWER",
                                "teamId", teamId
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        long userId = objectMapper.readTree(
                created.getResponse().getContentAsString()).get("id").asLong();

        String userToken = login("disable-me@example.com", "disable-me-password");

        mockMvc.perform(patch("/api/v1/users/{id}/status", userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("status", "DISABLED"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapCanOnlyRunOnce() throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teamName", "Second Bootstrap",
                                "teamSlug", "second-bootstrap",
                                "email", "second@example.com",
                                "displayName", "Second Admin",
                                "password", "second-bootstrap-password"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Bootstrap is only available before the first user is created"));
    }

    private String createUserAndLogin(String email, String role, String password)
            throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "displayName", email,
                                "password", password,
                                "role", role,
                                "teamId", teamId
                        ))))
                .andExpect(status().isCreated());

        return login(email, password);
    }

    private String login(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
