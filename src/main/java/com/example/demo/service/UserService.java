package com.example.demo.service;

import com.example.demo.dto.ChangePasswordRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.audit.SecurityAuditLogger;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Single point of access for user entities. Other auth domain services delegate
 * user retrieval and persistence here instead of using UserRepository directly,
 * keeping persistence coupling in one place and making the auth subdomains
 * easier to test and evolve independently.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final SecurityAuditLogger securityAuditLogger;

    /**
     * Returns a user by username or email. Throws BadCredentialsException on
     * failure to avoid leaking whether the identifier exists.
     */
    @Transactional(readOnly = true)
    public User getByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));
    }

    /**
     * Loads the user entity by username, throwing the standard Spring Security
     * exception when not found.
     */
    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found: " + username));
    }

    /**
     * Loads a user by id. Used by management services that operate on a specific
     * user record rather than the currently authenticated one.
     */
    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    /**
     * Returns all users. Delegated to by management services that need to list
     * user accounts.
     */
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Returns all users in a paginated form.
     */
    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    /**
     * Returns a paginated list of users that belong to the given tenant.
     */
    @Transactional(readOnly = true)
    public Page<User> findAllByTenantId(Long tenantId, Pageable pageable) {
        return userRepository.findAllByTenantId(tenantId, pageable);
    }

    /**
     * Loads a user by id constrained to the given tenant.
     */
    @Transactional(readOnly = true)
    public User getByIdAndTenantId(Long id, Long tenantId) {
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Builds the public user profile DTO and converts the role set to a
     * String array, avoiding entity exposure through the API.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String username) {
        User user = getByUsername(username);
        UserResponse response = modelMapper.map(user, UserResponse.class);
        response.setRoles(user.getRoles().stream().map(Role::getName).toArray(String[]::new));
        return response;
    }

    /**
     * Requires the current password before encoding the new one, ensuring the
     * existing credential must be known even if the session is authenticated.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request, String ipAddress) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        User user = getByUsername(username);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        securityAuditLogger.logPasswordChanged(user.getUsername(), ipAddress);
    }

    /**
     * Exposed for other domain services to persist user state changes without
     * coupling themselves directly to the repository.
     */
    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    /**
     * Deletes a user. Used by management services after ownership and safety
     * checks have been performed.
     */
    @Transactional
    public void delete(User user) {
        userRepository.delete(user);
    }

    /**
     * Returns the number of users that have the given role.
     */
    @Transactional(readOnly = true)
    public long countByRoleName(String roleName) {
        return userRepository.countByRolesName(roleName);
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
