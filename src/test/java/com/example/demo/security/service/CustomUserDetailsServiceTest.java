package com.example.demo.security.service;

import com.example.demo.constants.UserPermission;
import com.example.demo.entity.Department;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User user;

    @BeforeEach
    void setUp() {
        Role employeeRole = Role.builder()
                .name("EMPLOYEE")
                .permissions(Set.of(UserPermission.EXPENSE_READ))
                .build();
        Tenant tenant = Tenant.builder().id(1L).name("Acme Corp").build();
        Department department = Department.builder().id(1L).name("Engineering").tenant(tenant).build();

        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("secret")
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .tenant(tenant)
                .department(department)
                .build();
        user.setRoles(new HashSet<>(Set.of(employeeRole)));

        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("loadUserByEmail returns the user")
    void loadUserByEmailSuccess() {
        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded).isSameAs(user);
        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("loadUserByEmail for an unknown user throws UsernameNotFoundException")
    void loadUserByEmailUnknownUser() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found: unknown@example.com");
    }

    @Test
    @DisplayName("loadUserEntityByEmail returns the user entity")
    void loadUserEntityByEmailSuccess() {
        User loaded = customUserDetailsService.loadUserEntityByEmail("test@example.com");

        assertThat(loaded).isSameAs(user);
    }

    @Test
    @DisplayName("loadUserEntityByEmail for an unknown user throws UsernameNotFoundException")
    void loadUserEntityByEmailUnknown() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserEntityByEmail("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found: unknown@example.com");
    }

    @Test
    @DisplayName("A disabled user is returned with isEnabled false so the provider throws DisabledException")
    void disabledUserPreservesEnabledFlag() {
        user.setEnabled(false);

        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("A locked user is returned with isAccountNonLocked false so the provider throws LockedException")
    void lockedUserPreservesAccountNonLockedFlag() {
        user.setAccountNonLocked(false);

        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("An expired account is returned with isAccountNonExpired false so the provider throws AccountExpiredException")
    void expiredAccountPreservesAccountNonExpiredFlag() {
        user.setAccountNonExpired(false);

        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.isAccountNonExpired()).isFalse();
    }

    @Test
    @DisplayName("Expired credentials are returned with isCredentialsNonExpired false so the provider throws CredentialsExpiredException")
    void expiredCredentialsPreservesCredentialsNonExpiredFlag() {
        user.setCredentialsNonExpired(false);

        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.isCredentialsNonExpired()).isFalse();
    }

    @Test
    @DisplayName("A fresh user has all four account flags true")
    void freshUserHasAllFlagsTrue() {
        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.isAccountNonExpired()).isTrue();
        assertThat(loaded.isAccountNonLocked()).isTrue();
        assertThat(loaded.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("Authorities contain the ROLE_-prefixed role and the raw permission names")
    void authoritiesIncludeRoleAndPermissions() {
        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_EMPLOYEE", "EXPENSE_READ");
    }

    @Test
    @DisplayName("A user with no roles has empty authorities")
    void userWithNoRolesHasEmptyAuthorities() {
        user.getRoles().clear();

        UserDetails loaded = customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Roles, permissions, tenant, and department are initialized on the returned user")
    void associationsAreInitialized() {
        User loaded = (User) customUserDetailsService.loadUserByUsername("test@example.com");

        assertThat(loaded.getRoles()).isNotEmpty();
        assertThat(loaded.getRoles()).allSatisfy(role -> assertThat(role.getPermissions()).isNotEmpty());
        assertThat(loaded.getTenant()).isNotNull();
        assertThat(loaded.getDepartment()).isNotNull();
    }
}
