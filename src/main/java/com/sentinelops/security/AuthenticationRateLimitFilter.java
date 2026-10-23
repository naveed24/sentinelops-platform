package com.sentinelops.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Protects anonymous credential endpoints before body parsing or password hashing.
 * Redis outages fail closed for these endpoints only; the rest of the API remains usable.
 */
@Component
@Profile("rate-limit")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private final AuthenticationRateLimiter limiter;

    public AuthenticationRateLimitFilter(AuthenticationRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
        return !"/api/v1/auth/login".equals(path)
                && !"/api/v1/auth/bootstrap".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        AuthenticationRateLimiter.RateLimitDecision decision;
        try {
            decision = limiter.check(request.getRemoteAddr());
        } catch (DataAccessException | IllegalStateException ex) {
            response.setHeader("Retry-After", "1");
            writeError(response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication temporarily unavailable");
            return;
        }

        if (!decision.allowed()) {
            response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
            writeError(response, HttpStatus.TOO_MANY_REQUESTS,
                    "Authentication request limit exceeded");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                + "\",\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message
                + "\",\"fieldErrors\":{}}");
    }
}
