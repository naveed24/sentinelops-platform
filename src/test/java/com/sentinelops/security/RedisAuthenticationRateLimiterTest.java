package com.sentinelops.security;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisAuthenticationRateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void usesAtomicRedisScriptAndHashedPerAddressKey() {
        doReturn(List.of(11L, 1201L)).when(redis)
                .execute(any(RedisScript.class), anyList(), anyString());
        var limiter = new RedisAuthenticationRateLimiter(redis, 10, 60);

        var decision = limiter.check("203.0.113.9");

        assertFalse(decision.allowed());
        assertEquals(2, decision.retryAfterSeconds());

        ArgumentCaptor<RedisScript> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(script.capture(), keys.capture(), eq("60000"));
        assertTrue(script.getValue().getScriptAsString().contains("redis.call('INCR'"));
        assertTrue(script.getValue().getScriptAsString().contains("redis.call('PEXPIRE'"));
        assertTrue(script.getValue().getScriptAsString().contains("redis.call('PTTL'"));
        String key = (String) keys.getValue().get(0);
        assertTrue(key.startsWith("sentinelops:auth:rate:"));
        assertFalse(key.contains("203.0.113.9"));
    }

    @Test
    void allowsRequestsWithinConfiguredWindow() {
        doReturn(List.of(10L, 60000L)).when(redis)
                .execute(any(RedisScript.class), anyList(), anyString());

        assertTrue(new RedisAuthenticationRateLimiter(redis, 10, 60)
                .check("203.0.113.9").allowed());
    }

    @Test
    void failsClosedOnUnexpectedRedisResponse() {
        assertThrows(IllegalStateException.class,
                () -> new RedisAuthenticationRateLimiter(redis, 10, 60)
                        .check("203.0.113.9"));
    }

    @Test
    void rejectsInvalidRateLimitConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new RedisAuthenticationRateLimiter(redis, 0, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new RedisAuthenticationRateLimiter(redis, 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new RedisAuthenticationRateLimiter(redis, 10, 86401));
    }
}
