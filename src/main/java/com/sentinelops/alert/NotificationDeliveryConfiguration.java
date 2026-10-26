package com.sentinelops.alert;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;

/**
 * No external sends occur by default. Operators must explicitly opt in and
 * configure a real HTTPS endpoint and HMAC secret.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sentinelops.notifications.delivery", name = "enabled", havingValue = "true")
public class NotificationDeliveryConfiguration {
    @Bean
    NotificationDeliveryPort notificationDeliveryPort(
            @Value("${sentinelops.notifications.delivery.webhook.url:}") String url,
            @Value("${sentinelops.notifications.delivery.webhook.secret:}") String secret,
            @Value("${sentinelops.notifications.delivery.webhook.timeout:PT10S}") Duration timeout,
            ObjectMapper mapper) {
        return new WebhookNotificationDeliveryPort(url, secret, timeout, mapper);
    }

    @Bean
    NotificationDeliveryWorker notificationDeliveryWorker(
            NotificationOutboxClaimService claims, NotificationOutboxCompletionService completions,
            NotificationDeliveryPort port, MeterRegistry metrics,
            @Value("${sentinelops.notifications.delivery.batch-size:10}") int batchSize,
            @Value("${sentinelops.notifications.delivery.max-attempts:5}") int maxAttempts,
            @Value("${sentinelops.notifications.delivery.lease:PT1M}") Duration lease,
            @Value("${sentinelops.notifications.delivery.retry-base:PT30S}") Duration retryBase,
            @Value("${sentinelops.notifications.delivery.retry-cap:PT1H}") Duration retryCap) {
        return new NotificationDeliveryWorker(claims, completions, port, metrics,
                new NotificationDeliveryWorker.Settings(batchSize, maxAttempts, lease,
                        retryBase, retryCap), Clock.systemUTC());
    }
}
