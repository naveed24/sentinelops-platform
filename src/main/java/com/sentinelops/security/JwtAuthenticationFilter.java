package com.sentinelops.security;

import com.sentinelops.identity.UserAccountRepository;
import com.sentinelops.identity.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository users;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        var headers = request.getHeaders("Authorization");
        if (headers == null || !headers.hasMoreElements()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = headers.nextElement();
        if (headers.hasMoreElements()) {
            reject(response, "Multiple Authorization headers are not allowed");
            return;
        }

        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // HTTP authentication schemes are case-insensitive (RFC 7235).
        if (!authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(response, "Authorization header must use Bearer authentication");
            return;
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            reject(response, "Access token is missing");
            return;
        }

        JwtService.TokenClaims claims;
        try {
            claims = jwtService.parseAndValidate(token);
        } catch (IllegalArgumentException ex) {
            reject(response, "Invalid or expired access token");
            return;
        }

        // Do not catch database or downstream application exceptions here.
        // They are operational failures, not authentication failures.
        var user = users.findByEmailIgnoreCase(claims.subject()).orElse(null);
        if (user == null
                || !Objects.equals(user.getId(), claims.userId())
                || user.getStatus() != UserStatus.ACTIVE
                || user.getRole() != claims.role()) {
            reject(response, "Invalid or expired access token");
            return;
        }

        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        var authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(authority));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
    }
}
