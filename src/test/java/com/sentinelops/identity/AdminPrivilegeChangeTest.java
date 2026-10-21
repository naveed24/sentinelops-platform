package com.sentinelops.identity;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPrivilegeChangeTest {
    @Test
    void anotherActiveAdminAllowsDemotion() {
        var users = mock(UserAccountRepository.class);
        var team = Team.builder().id(1L).slug("ops").build();
        var first = UserAccount.builder().id(1L)
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).team(team).build();
        var second = UserAccount.builder().id(2L)
                .role(UserRole.ADMIN).status(UserStatus.ACTIVE).team(team).build();
        when(users.lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        when(users.findById(1L)).thenReturn(Optional.of(first));
        when(users.saveAndFlush(first)).thenReturn(first);

        var service = new UserAccountService(users, null, null);
        var response = service.updateRole(
                1L, new IdentityDtos.UpdateRoleRequest(UserRole.RESPONDER));

        assertThat(response.role()).isEqualTo(UserRole.RESPONDER);
        assertThat(second.getRole()).isEqualTo(UserRole.ADMIN);
        verify(users).saveAndFlush(first);
    }
}
