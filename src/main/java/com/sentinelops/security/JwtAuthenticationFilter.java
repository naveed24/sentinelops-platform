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

        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Authorization header must use Bearer authentication");
            return;
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Access token is missing");
            return;
        }

        try {
            JwtService.TokenClaims claims = jwtService.parseAndValidate(token);

            var user = users.findByEmailIgnoreCase(claims.subject())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown token subject"));

            if (!user.getId().equals(claims.userId())
                    || user.getStatus() != UserStatus.ACTIVE
                    || user.getRole() != claims.role()) {
                throw new IllegalArgumentException(
                        "Token no longer matches the active user account");
            }

            var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.getEmail(), null, List.of(authority));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired access token");
        }
    }
}
