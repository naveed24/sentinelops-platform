package com.sentinelops.identity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.TokenResponse bootstrap(
            @Valid @RequestBody AuthDtos.BootstrapRequest request) {
        return authService.bootstrap(request);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }
}
