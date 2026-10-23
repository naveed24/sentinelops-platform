package com.sentinelops.security;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationRateLimitFilterTest {

    private final AuthenticationRateLimiter limiter = mock(AuthenticationRateLimiter.class);
    private final AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(limiter);

    @Test
    void throttlesLoginWithRetryAfterAndDoesNotInvokeApplication() throws Exception {
        when(limiter.check("203.0.113.9")).thenReturn(
                new AuthenticationRateLimiter.RateLimitDecision(false, 17));
        var request = request("POST", "/api/v1/auth/login");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertEquals(429, response.getStatus());
        assertEquals("17", response.getHeader("Retry-After"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getContentAsString().contains("Authentication request limit exceeded"));
        assertFalse(continued.get());
    }

    @Test
    void countsBootstrapAndUsesSocketAddressInsteadOfSpoofableForwardedHeader() throws Exception {
        when(limiter.check("203.0.113.9")).thenReturn(
                new AuthenticationRateLimiter.RateLimitDecision(true, 60));
        var request = request("POST", "/api/v1/auth/bootstrap");
        request.addHeader("X-Forwarded-For", "198.51.100.88");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> continued.set(true));

        assertTrue(continued.get());
        verify(limiter).check("203.0.113.9");
    }

    @Test
    void failsClosedOnlyForAuthenticationEndpointsWhenRedisIsUnavailable() throws Exception {
        when(limiter.check("203.0.113.9")).thenThrow(
                new DataAccessResourceFailureException("redis unavailable"));
        var login = request("POST", "/api/v1/auth/login");
        var response = new MockHttpServletResponse();
        var continued = new AtomicBoolean();

        filter.doFilter(login, response, (req, res) -> continued.set(true));

        assertEquals(503, response.getStatus());
        assertEquals("1", response.getHeader("Retry-After"));
        assertFalse(continued.get());
        assertFalse(response.getContentAsString().contains("redis unavailable"));

        filter.doFilter(request("GET", "/api/v1/incidents"), new MockHttpServletResponse(),
                (req, res) -> continued.set(true));
        assertTrue(continued.get());
        verify(limiter, times(1)).check(anyString());
    }

    @Test
    void ignoresUnrelatedPathsAndNonPostRequests() throws Exception {
        var continued = new AtomicBoolean();
        filter.doFilter(request("GET", "/api/v1/auth/login"), new MockHttpServletResponse(),
                (req, res) -> continued.set(true));
        assertTrue(continued.get());
        verifyNoInteractions(limiter);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.9");
        return request;
    }
}
