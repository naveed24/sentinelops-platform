package com.sentinelops.security;

import com.sentinelops.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterInvalidTokenTest {

    @Test
    void invalidTokenReturns401WithoutCallingApplication() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(UserAccountRepository.class);
        when(jwt.parseAndValidate("invalid")).thenThrow(
                new IllegalArgumentException("Invalid signature"));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        var response = new MockHttpServletResponse();

        new JwtAuthenticationFilter(jwt, users).doFilter(request, response,
                (req, res) -> { throw new AssertionError("Invalid token passed filter"); });

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(users);
    }
}
