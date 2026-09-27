package com.sentinelops.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ServiceDtos {
    private ServiceDtos() {}

    public record CreateServiceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 120) String ownerTeam
    ) {}

    public record UpdateStatusRequest(ServiceStatus status) {}

    public record ServiceResponse(
            Long id,
            String name,
            String description,
            String ownerTeam,
            ServiceStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ServiceResponse from(ServiceRecord service) {
            return new ServiceResponse(
                    service.getId(), service.getName(), service.getDescription(),
                    service.getOwnerTeam(), service.getStatus(),
                    service.getCreatedAt(), service.getUpdatedAt()
            );
        }
    }
}
