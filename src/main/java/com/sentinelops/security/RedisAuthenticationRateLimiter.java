package com.sentinelops.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Fixed-window limit shared by all application instances. INCR and key expiry
 * happen atomically in Redis, so concurrent requests cannot exceed the limit.
 */
@Component
@Profile("rate-limit")
public class RedisAuthenticationRateLimiter implements AuthenticationRateLimiter {

    private static final RedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;
    private final int maxRequests;
    private final long windowMillis;

    public RedisAuthenticationRateLimiter(
            StringRedisTemplate redis,
            @Value("${sentinelops.rate-limit.max-requests:10}") int maxRequests,
            @Value("${sentinelops.rate-limit.window-seconds:60}") long windowSeconds) {
        if (maxRequests < 1 || windowSeconds < 1 || windowSeconds > 86_400) {
            throw new IllegalArgumentException(
                    "Rate limit requires positive max-requests and window-seconds in [1, 86400]");
        }
        this.redis = redis;
        this.maxRequests = maxRequests;
        this.windowMillis = Math.multiplyExact(windowSeconds, 1_000L);
    }

    @Override
    public RateLimitDecision check(String remoteAddress) {
        // Never trust caller-controlled forwarding headers for an authentication limit.
        String address = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown" : remoteAddress;
        String key = "sentinelops:auth:rate:" + sha256(address);
        List<?> state = redis.execute(SCRIPT, List.of(key), Long.toString(windowMillis));
        if (state == null || state.size() != 2
                || !(state.get(0) instanceof Number count)
                || !(state.get(1) instanceof Number ttl)) {
            throw new IllegalStateException("Invalid Redis rate-limit response");
        }
        long retryAfterSeconds = Math.max(1L, (ttl.longValue() + 999L) / 1_000L);
        return new RateLimitDecision(count.longValue() <= maxRequests, retryAfterSeconds);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
