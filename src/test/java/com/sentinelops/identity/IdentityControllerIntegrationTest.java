package com.sentinelops.identity;

import tools.jackson.databind.ObjectMapper;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository userAccounts;
    @Autowired TeamRepository teams;

    private String adminToken;

    @BeforeEach
    void bootstrapAdmin() throws Exception {
        userAccounts.deleteAll();
        teams.deleteAll();

        var result = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teamName", "Test Operations",
                                "teamSlug", "test-operations",
                                "email", "admin@example.com",
                                "displayName", "Test Admin",
                                "password", "correct-horse-battery"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        adminToken = objectMapper.readTree(
                result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void createsTeamAndUserThenUpdatesRbacState() throws Exception {
        var teamResult = mockMvc.perform(authorized(post("/api/v1/teams"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Platform Operations",
                                "slug", "platform-operations"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Platform Operations"))
                .andExpect(jsonPath("$.slug").value("platform-operations"))
                .andReturn();

        long teamId = objectMapper.readTree(
                teamResult.getResponse().getContentAsString()).get("id").asLong();

        var userResult = mockMvc.perform(authorized(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "OnCall@Example.com",
                                "displayName", "Primary On Call",
                                "password", "another-secure-password",
                                "role", "RESPONDER",
                                "teamId", teamId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("oncall@example.com"))
                .andExpect(jsonPath("$.role").value("RESPONDER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.teamId").value(teamId))
                .andExpect(jsonPath("$.teamSlug").value("platform-operations"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        long userId = objectMapper.readTree(
                userResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(authorized(patch("/api/v1/users/{id}/role", userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(authorized(patch("/api/v1/users/{id}/status", userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mockMvc.perform(authorized(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "oncall@example.com",
                                "displayName", "Duplicate",
                                "password", "duplicate-secure-password",
                                "role", "VIEWER",
                                "teamId", teamId
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "User email already exists: oncall@example.com"));
    }

    @Test
    void rejectsInvalidTeamSlugAndUnknownTeam() throws Exception {
        mockMvc.perform(authorized(post("/api/v1/teams"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Invalid Team",
                                "slug", "Invalid Slug"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.slug").exists());

        mockMvc.perform(authorized(post("/api/v1/users"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "unknown-team@example.com",
                                "displayName", "Unknown Team User",
                                "password", "unknown-team-password",
                                "role", "VIEWER",
                                "teamId", 999999
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Team not found: 999999"));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
    }
}
