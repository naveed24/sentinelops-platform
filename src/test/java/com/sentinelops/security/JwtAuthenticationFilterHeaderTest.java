package com.sentinelops.security;

import com.sentinelops.identity.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterHeaderTest {

    @Test
    void duplicateAuthorizationHeadersAreRejected() throws Exception {
        var jwt = mock(JwtService.class);
        var users = mock(UserAccountRepository.class);
        var filter = new JwtAuthenticationFilter(jwt, users);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer first");
        request.addHeader("Authorization", "Bearer second");
        var response = new MockHttpServletResponse();
        var downstream = new AtomicBoolean();

        filter.doFilter(request, response, (req, res) -> downstream.set(true));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(downstream).isFalse();
        verifyNoInteractions(jwt, users);
    }

    @Test
    void unsupportedSchemeIsRejected() throws Exception {
        var filter = new JwtAuthenticationFilter(null, null);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic credentials");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response,
                (req, res) -> { throw new AssertionError("Must not reach application"); });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void missingHeaderIsLeftForAuthorizationRules() throws Exception {
        var filter = new JwtAuthenticationFilter(null, null);
        var downstream = new AtomicBoolean();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> downstream.set(true));

        assertThat(downstream).isTrue();
    }
}
