package com.sentinelops.identity;

import com.sentinelops.common.ConflictException;
import com.sentinelops.common.UnauthorizedException;
import com.sentinelops.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserAccountRepository users;
    private final TeamService teams;
    private final UserAccountService userAccounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthDtos.TokenResponse bootstrap(AuthDtos.BootstrapRequest request) {
        if (users.count() != 0) {
            throw new ConflictException(
                    "Bootstrap is only available before the first user is created");
        }

        var team = teams.create(new IdentityDtos.CreateTeamRequest(
                request.teamName(), request.teamSlug()));

        userAccounts.create(new IdentityDtos.CreateUserRequest(
                request.email(),
                request.displayName(),
                request.password(),
                UserRole.ADMIN,
                team.id()
        ));

        return login(new AuthDtos.LoginRequest(request.email(), request.password()));
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        UserAccount user = users.findByEmailIgnoreCase(email)
                .orElseThrow(this::invalidCredentials);

        if (user.getStatus() != UserStatus.ACTIVE
                || user.getPasswordHash() == null
                || !user.getPasswordHash().startsWith("$2")
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthDtos.TokenResponse(
                "Bearer",
                token.value(),
                token.expiresAt(),
                IdentityDtos.UserResponse.from(user)
        );
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("Invalid email or password");
    }
}
