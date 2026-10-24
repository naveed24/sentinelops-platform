package com.sentinelops.alert;

import com.sentinelops.incident.IncidentSeverity;
import jakarta.validation.constraints.*;

import java.time.Instant;

public final class AlertRuleDtos {
    private AlertRuleDtos() {}

    public record CreateRequest(
            @NotNull @Positive Long serviceId,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String eventType,
            @NotNull IncidentSeverity severity,
            @NotNull Boolean enabled,
            @NotNull @Min(0) @Max(86400) Integer cooldownSeconds) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String eventType,
            @NotNull IncidentSeverity severity,
            @NotNull Boolean enabled,
            @NotNull @Min(0) @Max(86400) Integer cooldownSeconds,
            @NotNull @PositiveOrZero Long expectedVersion) {}

    public record Response(
            Long id, Long serviceId, String name, String eventType,
            IncidentSeverity severity, boolean enabled, int cooldownSeconds,
            Long version, Instant createdAt, Instant updatedAt) {
        public static Response from(AlertRule rule) {
            return new Response(rule.getId(), rule.getService().getId(), rule.getName(),
                    rule.getEventType(), rule.getSeverity(), rule.isEnabled(),
                    rule.getCooldownSeconds(), rule.getVersion(),
                    rule.getCreatedAt(), rule.getUpdatedAt());
        }
    }
}
