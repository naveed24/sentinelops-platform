package com.sentinelops.identity;

import com.sentinelops.common.ConflictException;
import com.sentinelops.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountService {

    private final UserAccountRepository users;
    private final TeamService teams;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public IdentityDtos.UserResponse create(IdentityDtos.CreateUserRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("User email already exists: " + email);
        }

        UserAccount user = UserAccount.builder()
                .email(email)
                .displayName(request.displayName().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .status(UserStatus.ACTIVE)
                .team(teams.find(request.teamId()))
                .build();

        return IdentityDtos.UserResponse.from(users.saveAndFlush(user));
    }

    public List<IdentityDtos.UserResponse> list() {
        return users.findAll(Sort.by(Sort.Direction.ASC, "email")).stream()
                .map(IdentityDtos.UserResponse::from)
                .toList();
    }

    public IdentityDtos.UserResponse get(Long id) {
        return IdentityDtos.UserResponse.from(find(id));
    }

    @Transactional
    public IdentityDtos.UserResponse updateRole(Long id, IdentityDtos.UpdateRoleRequest request) {
        int activeAdminCount = lockActiveAdmins();
        UserAccount user = find(id);
        protectLastActiveAdmin(user, request.role(), user.getStatus(), activeAdminCount);
        user.setRole(request.role());
        return IdentityDtos.UserResponse.from(users.saveAndFlush(user));
    }

    @Transactional
    public IdentityDtos.UserResponse updateStatus(Long id, IdentityDtos.UpdateStatusRequest request) {
        int activeAdminCount = lockActiveAdmins();
        UserAccount user = find(id);
        protectLastActiveAdmin(user, user.getRole(), request.status(), activeAdminCount);
        user.setStatus(request.status());
        return IdentityDtos.UserResponse.from(users.saveAndFlush(user));
    }

    private int lockActiveAdmins() {
        // Lock all active administrators in ID order before loading the target.
        // Concurrent demotions/deactivations cannot both pass a stale count check.
        return users.lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE).size();
    }

    private void protectLastActiveAdmin(
            UserAccount current, UserRole newRole, UserStatus newStatus, int activeAdminCount) {
        boolean removingAccess = current.getRole() == UserRole.ADMIN
                && current.getStatus() == UserStatus.ACTIVE
                && (newRole != UserRole.ADMIN || newStatus != UserStatus.ACTIVE);
        if (removingAccess && activeAdminCount <= 1) {
            throw new ConflictException("Cannot remove the last active administrator");
        }
    }

    private UserAccount find(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }
}
