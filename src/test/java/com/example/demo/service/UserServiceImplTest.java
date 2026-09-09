package com.example.demo.service;

import com.example.demo.dto.ChangePasswordRequest;
import com.example.demo.dto.UserProfileResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.service.RefreshTokenService;
import com.example.demo.service.impl.UserServiceImpl;
import com.example.demo.mapper.UserManagementMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserManagementMapper userManagementMapper;

    @Mock
    private SecurityAuditLogger securityAuditLogger;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private UserProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        Tenant tenant = Tenant.builder().id(1L).name("Tenant 1").build();
        Role role = Role.builder()
                .id(1L)
                .name("EMPLOYEE")
                .permissions(java.util.Set.of(com.example.demo.constants.UserPermission.USER_READ))
                .build();
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("encoded")
                .tenant(tenant)
                .build();
        user.getRoles().add(role);
        profileResponse = UserProfileResponse.builder()
                .email("test@example.com")
                .firstName(null)
                .lastName(null)
                .roles(new String[]{"EMPLOYEE"})
                .permissions(java.util.Set.of("USER_READ"))
                .enabled(true)
                .mfaEnabled(false)
                .mfaMethod("NONE")
                .build();

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userManagementMapper.toProfileResponse(any(User.class))).thenReturn(profileResponse);
    }

    @Test
    @DisplayName("existsByEmail delegates to the repository and returns its result")
    void existsByEmailDelegatesToRepo() {
        when(userRepository.existsByEmail("testuser")).thenReturn(true);

        assertThat(userService.existsByEmail("testuser")).isTrue();
        verify(userRepository).existsByEmail("testuser");
    }

    @Test
    @DisplayName("existsByEmail delegates to the repository and returns its result")
    void existsByEmailDelegates() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);

        assertThat(userService.existsByEmail("test@example.com")).isFalse();
        verify(userRepository).existsByEmail("test@example.com");
    }

    @Test
    @DisplayName("getByEmail returns the user when found")
    void getByEmailReturnsUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThat(userService.getByEmail("test@example.com")).isSameAs(user);
    }

    @Test
    @DisplayName("getByEmail throws UsernameNotFoundException when not found")
    void getByEmailNotFoundThrows() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByEmail("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found: missing@example.com");
    }

    @Test
    @DisplayName("getById returns the user when found")
    void getByIdReturnsUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.getById(1L)).isSameAs(user);
    }

    @Test
    @DisplayName("getById throws IllegalArgumentException when not found")
    void getByIdNotFoundThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("getByIdAndTenantId returns the user when found")
    void getByIdAndTenantIdReturnsUser() {
        when(userRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(user));

        assertThat(userService.getByIdAndTenantId(1L, 1L)).isSameAs(user);
    }

    @Test
    @DisplayName("getByIdAndTenantId throws IllegalArgumentException when not found")
    void getByIdAndTenantIdNotFoundThrows() {
        when(userRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getByIdAndTenantId(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("findByEmail returns the user when present")
    void findByEmailReturnsPresentOptional() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThat(userService.findByEmail("test@example.com")).containsSame(user);
    }

    @Test
    @DisplayName("findByEmail returns an empty Optional when not found")
    void findByEmailReturnsEmptyOptional() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThat(userService.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    @DisplayName("findAll delegates to the repository")
    void findAllDelegates() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<User> result = userService.findAll(pageable);

        assertThat(result.getContent()).containsExactly(user);
        verify(userRepository).findAll(pageable);
    }

    @Test
    @DisplayName("findAllByTenantId delegates to the repository")
    void findAllByTenantIdDelegates() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(userRepository.findAllByTenantId(1L, pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<User> result = userService.findAllByTenantId(1L, pageable);

        assertThat(result.getContent()).containsExactly(user);
        verify(userRepository).findAllByTenantId(1L, pageable);
    }

    @Test
    @DisplayName("getCurrentUser maps the user with roles and permissions")
    void getCurrentUserMapsProfile() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        UserProfileResponse result = userService.getCurrentUser("test@example.com");

        assertThat(result).isSameAs(profileResponse);
        assertThat(result.getRoles()).containsExactly("EMPLOYEE");
        assertThat(result.getPermissions()).containsExactly("USER_READ");
        assertThat(result.getMfaEnabled()).isFalse();
        assertThat(result.getMfaMethod()).isEqualTo("NONE");
        verify(userManagementMapper).toProfileResponse(user);
    }

    @Test
    @DisplayName("Changing password with matching confirmation encodes the new password, saves and revokes tokens")
    void changePasswordSuccess() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass")
                .newPassword("newPass123!")
                .confirmPassword("newPass123!")
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "encoded")).thenReturn(true);
        when(passwordEncoder.encode("newPass123!")).thenReturn("encoded-new");

        userService.changePassword("test@example.com", request, "127.0.0.1");

        assertThat(user.getPassword()).isEqualTo("encoded-new");
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllUserRefreshTokens("test@example.com");
        verify(securityAuditLogger).logPasswordChanged("test@example.com", "127.0.0.1");
    }

    @Test
    @DisplayName("Changing password with mismatched confirmation throws and never saves, revokes or audits")
    void changePasswordMismatchThrows() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("oldPass")
                .newPassword("newPass123!")
                .confirmPassword("different")
                .build();

        assertThatThrownBy(() -> userService.changePassword("test@example.com", request, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Passwords do not match");
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeAllUserRefreshTokens(anyString());
        verify(securityAuditLogger, never()).logPasswordChanged(anyString(), anyString());
    }

    @Test
    @DisplayName("Changing password with a wrong current password throws and never saves, revokes or audits")
    void changePasswordWrongCurrentThrows() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrongPass")
                .newPassword("newPass123!")
                .confirmPassword("newPass123!")
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword("test@example.com", request, "127.0.0.1"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Current password is incorrect");
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revokeAllUserRefreshTokens(anyString());
        verify(securityAuditLogger, never()).logPasswordChanged(anyString(), anyString());
    }

    @Test
    @DisplayName("save delegates to the repository and returns the saved user")
    void saveDelegates() {
        assertThat(userService.save(user)).isSameAs(user);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("delete delegates to the repository")
    void deleteDelegates() {
        userService.delete(user);

        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("countByRoleName delegates to the repository and returns the count")
    void countByRoleNameDelegates() {
        when(userRepository.countByRolesName("EMPLOYEE")).thenReturn(3L);

        assertThat(userService.countByRoleName("EMPLOYEE")).isEqualTo(3L);
        verify(userRepository).countByRolesName("EMPLOYEE");
    }

    @Test
    @DisplayName("countByRoleNameAndTenantId delegates to the repository and returns the count")
    void countByRoleNameAndTenantIdDelegates() {
        when(userRepository.countByRolesNameAndTenantId("EMPLOYEE", 1L)).thenReturn(2L);

        assertThat(userService.countByRoleNameAndTenantId("EMPLOYEE", 1L)).isEqualTo(2L);
        verify(userRepository).countByRolesNameAndTenantId("EMPLOYEE", 1L);
    }
}
