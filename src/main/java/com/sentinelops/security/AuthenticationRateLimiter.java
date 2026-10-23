package com.sentinelops.security;

/** Shared contract so HTTP handling does not depend on Redis implementation details. */
interface AuthenticationRateLimiter {
    RateLimitDecision check(String remoteAddress);

    record RateLimitDecision(boolean allowed, long retryAfterSeconds) {}
}
