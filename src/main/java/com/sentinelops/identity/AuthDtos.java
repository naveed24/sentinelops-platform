package com.sentinelops.identity;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record BootstrapRequest(
            @NotBlank @Size(max = 120) String teamName,
            @NotBlank
            @Size(max = 80)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                    message = "must contain lowercase letters, numbers, and single hyphens only")
            String teamSlug,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 140) String displayName,
            @NotBlank @Size(min = 12, max = 128) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password
    ) {}

    public record TokenResponse(
            String tokenType,
            String accessToken,
            Instant expiresAt,
            IdentityDtos.UserResponse user
    ) {}
}
