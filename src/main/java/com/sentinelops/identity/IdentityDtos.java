package com.sentinelops.identity;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class IdentityDtos {
    private IdentityDtos() {}

    public record CreateTeamRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank
            @Size(max = 80)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                    message = "must contain lowercase letters, numbers, and single hyphens only")
            String slug
    ) {}

    public record TeamResponse(
            Long id,
            String name,
            String slug,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TeamResponse from(Team team) {
            return new TeamResponse(
                    team.getId(),
                    team.getName(),
                    team.getSlug(),
                    team.getCreatedAt(),
                    team.getUpdatedAt()
            );
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 140) String displayName,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotNull UserRole role,
            @NotNull Long teamId
    ) {}

    public record UpdateRoleRequest(@NotNull UserRole role) {}

    public record UpdateStatusRequest(@NotNull UserStatus status) {}

    public record UserResponse(
            Long id,
            String email,
            String displayName,
            UserRole role,
            UserStatus status,
            Long teamId,
            String teamSlug,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static UserResponse from(UserAccount user) {
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getRole(),
                    user.getStatus(),
                    user.getTeam().getId(),
                    user.getTeam().getSlug(),
                    user.getVersion(),
                    user.getCreatedAt(),
                    user.getUpdatedAt()
            );
        }
    }
}
