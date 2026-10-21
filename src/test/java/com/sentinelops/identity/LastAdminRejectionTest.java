package com.sentinelops.identity;

import com.sentinelops.common.ConflictException;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LastAdminRejectionTest {
    @Test
    void cannotDemoteOrDisableOnlyActiveAdmin() {
        var users = mock(UserAccountRepository.class);
        var admin = UserAccount.builder()
                .id(1L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build();
        when(users.lockUsersByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE))
                .thenReturn(List.of(admin));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        var service = new UserAccountService(users, null, null);

        assertThatThrownBy(() -> service.updateRole(
                1L, new IdentityDtos.UpdateRoleRequest(UserRole.VIEWER)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.updateStatus(
                1L, new IdentityDtos.UpdateStatusRequest(UserStatus.DISABLED)))
                .isInstanceOf(ConflictException.class);
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(users, never()).saveAndFlush(any());
    }
}
