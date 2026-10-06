package com.sentinelops.identity;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityDtos.UserResponse create(
            @Valid @RequestBody IdentityDtos.CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping
    public List<IdentityDtos.UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    public IdentityDtos.UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PatchMapping("/{id}/role")
    public IdentityDtos.UserResponse updateRole(
            @PathVariable Long id,
            @Valid @RequestBody IdentityDtos.UpdateRoleRequest request) {
        return userService.updateRole(id, request);
    }

    @PatchMapping("/{id}/status")
    public IdentityDtos.UserResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody IdentityDtos.UpdateStatusRequest request) {
        return userService.updateStatus(id, request);
    }
}
