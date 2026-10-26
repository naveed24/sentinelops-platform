package com.sentinelops.alert;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class WebhookNotificationDeliveryPortTest {
    @Test
    void refusesInsecureEndpointsAndWeakSecrets() {
        String secret = "x".repeat(32);
        assertThatThrownBy(() -> new WebhookNotificationDeliveryPort(
                "http://example.org/alerts", secret, Duration.ofSeconds(10), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new WebhookNotificationDeliveryPort(
                "https://example.org/alerts?token=unsafe", secret, Duration.ofSeconds(10), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> new WebhookNotificationDeliveryPort(
                "https://example.org/alerts", "short", Duration.ofSeconds(10), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void signsExactBodyWithStableHmacSha256() throws Exception {
        String first = WebhookNotificationDeliveryPort.sign("{\"eventId\":1}", "secret");
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(WebhookNotificationDeliveryPort.sign("{\"eventId\":1}", "secret")).isEqualTo(first);
        assertThat(WebhookNotificationDeliveryPort.sign("{\"eventId\":2}", "secret")).isNotEqualTo(first);
    }

    @Test
    void validatesWorkerBounds() {
        assertThatThrownBy(() -> new NotificationDeliveryWorker.Settings(
                0, 5, Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationDeliveryWorker.Settings(
                10, 0, Duration.ofMinutes(1), Duration.ofSeconds(30), Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
