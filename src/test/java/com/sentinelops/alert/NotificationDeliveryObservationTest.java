package com.sentinelops.alert;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationDeliveryObservationTest {
    private static final Instant NOW = Instant.parse("2035-01-01T00:00:00Z");

    @Test
    void successfulAttemptEmitsBoundedOutcomeAndTraceOnlyIdentity() throws Exception {
        var claims = mock(NotificationOutboxClaimService.class);
        var completions = mock(NotificationOutboxCompletionService.class);
        var seen = new ArrayList<Observation.Context>();
        var registry = recordingRegistry(seen);
        var payload = new NotificationDeliveryPayload(42L, 2L, 3L,
                "event-id", 4L, "ERROR_RATE", "SEV2", "prometheus", "threshold", NOW);
        when(claims.claimNext(eq(NOW), eq(3), any())).thenReturn(
                Optional.of(new NotificationOutboxClaimService.Claim(42L, "lease", 1, payload)),
                Optional.empty());
        when(completions.sent(42L, "lease", NOW)).thenReturn(true);
        NotificationDeliveryPort provider = mock(NotificationDeliveryPort.class);
        var worker = new NotificationDeliveryWorker(claims, completions, provider,
                new SimpleMeterRegistry(), registry, settings(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(worker.dispatchBatch()).isEqualTo(1);
        verify(provider).deliver(payload);
        assertThat(seen).hasSize(1);
        var context = seen.getFirst();
        assertThat(context.getName()).isEqualTo("sentinelops.notification.attempt");
        assertThat(context.getLowCardinalityKeyValues().toString()).contains("sent");
        assertThat(context.getHighCardinalityKeyValues().toString()).contains("42");
        assertThat(context.getLowCardinalityKeyValues().toString()).doesNotContain("42");
        assertThat(context.getHighCardinalityKeyValues().toString()).doesNotContain("threshold");
    }

    @Test
    void failedAttemptRecordsRetryWithoutLeakingProviderError() throws Exception {
        var claims = mock(NotificationOutboxClaimService.class);
        var completions = mock(NotificationOutboxCompletionService.class);
        var seen = new ArrayList<Observation.Context>();
        var registry = recordingRegistry(seen);
        var payload = new NotificationDeliveryPayload(55L, 2L, 3L,
                "event-id", 4L, "ERROR_RATE", "SEV2", "prometheus", "sensitive payload", NOW);
        when(claims.claimNext(eq(NOW), eq(3), any())).thenReturn(
                Optional.of(new NotificationOutboxClaimService.Claim(55L, "lease", 1, payload)),
                Optional.empty());
        when(completions.failed(eq(55L), eq("lease"), eq(false), any(), anyString()))
                .thenReturn(true);
        NotificationDeliveryPort provider = p -> {
            throw new NotificationDeliveryException("Webhook HTTP 503");
        };
        var worker = new NotificationDeliveryWorker(claims, completions, provider,
                new SimpleMeterRegistry(), registry, settings(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(worker.dispatchBatch()).isEqualTo(1);
        assertThat(seen).hasSize(1);
        var context = seen.getFirst();
        assertThat(context.getLowCardinalityKeyValues().toString()).contains("retry");
        assertThat(context.getHighCardinalityKeyValues().toString())
                .doesNotContain("sensitive payload", "Webhook HTTP");
        assertThat(context.getError()).hasMessage("notification_delivery_failed");
    }

    private static NotificationDeliveryWorker.Settings settings() {
        return new NotificationDeliveryWorker.Settings(10, 3, Duration.ofSeconds(60),
                Duration.ofSeconds(30), Duration.ofMinutes(10));
    }

    private static ObservationRegistry recordingRegistry(ArrayList<Observation.Context> seen) {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                seen.add(context);
            }
        });
        return registry;
    }
}
