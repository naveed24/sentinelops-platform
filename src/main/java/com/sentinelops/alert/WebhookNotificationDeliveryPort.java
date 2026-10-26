package com.sentinelops.alert;

import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * HTTPS webhook delivery with HMAC-SHA256 body authentication and a stable
 * idempotency key. Redirects are disabled and response bodies are discarded.
 */
public class WebhookNotificationDeliveryPort implements NotificationDeliveryPort {
    private final URI endpoint;
    private final String secret;
    private final Duration timeout;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public WebhookNotificationDeliveryPort(String url, String secret,
                                           Duration timeout, ObjectMapper mapper) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Notification webhook URL is required");
        }
        this.endpoint = URI.create(url);
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null
                || endpoint.getRawFragment() != null) {
            throw new IllegalArgumentException("Notification webhook must be an HTTPS URL without credentials, query or fragment");
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Notification webhook HMAC secret must contain at least 32 bytes");
        }
        if (timeout == null || timeout.compareTo(Duration.ofSeconds(1)) < 0
                || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Notification webhook timeout must be 1..30 seconds");
        }
        this.secret = secret;
        this.timeout = timeout;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public void deliver(NotificationDeliveryPayload payload) throws Exception {
        String body = mapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", "sentinelops-notification-" + payload.deliveryId())
                .header("X-SentinelOps-Delivery-Id", Long.toString(payload.deliveryId()))
                .header("X-SentinelOps-Signature", "sha256=" + sign(body, secret))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new NotificationDeliveryException("Webhook HTTP " + response.statusCode());
        }
    }

    static String sign(String body, String secret) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
