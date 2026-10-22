package com.sentinelops.ingestion;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

/** Versioned, bounded external contract. Event IDs are stable across retries. */
public record OperationalEventMessage(
        @NotNull Integer schemaVersion,
        @NotNull UUID eventId,
        @NotNull @Positive Long serviceId,
        @NotBlank @Size(max = 120) String source,
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String type,
        @NotBlank @Size(max = 500) String message,
        @NotNull Instant occurredAt) {
}
