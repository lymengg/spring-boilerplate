package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = "username"),
    @UniqueConstraint(columnNames = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(name = "first_name", length = 50)
    private String firstName;

    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "is_account_non_expired", nullable = false)
    @Builder.Default
    private Boolean accountNonExpired = true;

    @Column(name = "is_account_non_locked", nullable = false)
    @Builder.Default
    private Boolean accountNonLocked = true;

    @Column(name = "is_credentials_non_expired", nullable = false)
    @Builder.Default
    private Boolean credentialsNonExpired = true;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private int failedAttempts = 0;

    @Column(name = "account_locked_until")
    private Instant accountLockedUntil;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_method", nullable = false, length = 10)
    @Builder.Default
    private MfaMethod mfaMethod = MfaMethod.NONE;

    @Column(name = "mfa_secret", length = 255)
    private String mfaSecret;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        roles.forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            role.getPermissions().forEach(permission ->
                    authorities.add(new SimpleGrantedAuthority(permission.name())));
        });
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void incrementFailedAttempts() {
        this.failedAttempts++;
    }

    public void lockAccount(long lockoutDurationMinutes) {
        this.accountNonLocked = false;
        this.accountLockedUntil = Instant.now().plusSeconds(lockoutDurationMinutes * 60);
    }

    public void unlockAccount() {
        this.accountNonLocked = true;
        this.accountLockedUntil = null;
        this.failedAttempts = 0;
    }

    public boolean unlockIfExpired() {
        if (accountLockedUntil != null && Instant.now().isAfter(accountLockedUntil)) {
            unlockAccount();
            return true;
        }
        return false;
    }
}