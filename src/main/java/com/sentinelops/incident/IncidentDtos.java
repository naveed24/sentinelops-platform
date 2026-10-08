package com.sentinelops.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class IncidentDtos {
    private IncidentDtos() {}

    public record CreateIncidentRequest(
            @NotBlank @Size(max = 180) String title,
            @Size(max = 2000) String description,
            @NotNull IncidentSeverity severity,
            @NotNull Long serviceId) {}

    public record TransitionRequest(
            @NotNull IncidentStatus status,
            @NotBlank @Size(max = 500) String message,
            @PositiveOrZero Long expectedVersion) {
        public TransitionRequest(IncidentStatus status, String message) {
            this(status, message, null);
        }
    }

    public record IncidentResponse(
            Long id, String title, String description, IncidentSeverity severity,
            IncidentStatus status, Long serviceId, Long version,
            Instant createdAt, Instant updatedAt, Instant resolvedAt) {
        static IncidentResponse from(Incident incident) {
            return new IncidentResponse(incident.getId(), incident.getTitle(), incident.getDescription(),
                    incident.getSeverity(), incident.getStatus(), incident.getService().getId(),
                    incident.getVersion(), incident.getCreatedAt(), incident.getUpdatedAt(), incident.getResolvedAt());
        }
    }

    public record AuditResponse(Long id, IncidentStatus fromStatus, IncidentStatus toStatus,
                                String message, Instant createdAt) {
        static AuditResponse from(IncidentAuditEvent event) {
            return new AuditResponse(event.getId(), event.getFromStatus(), event.getToStatus(),
                    event.getMessage(), event.getCreatedAt());
        }
    }
}
