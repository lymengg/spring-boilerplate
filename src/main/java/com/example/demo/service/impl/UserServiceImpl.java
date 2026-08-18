package com.example.demo.service.impl;

import com.example.demo.dto.ChangePasswordRequest;
import com.example.demo.dto.UserProfileResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.audit.SecurityAuditLogger;
import com.example.demo.security.service.RefreshTokenService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Single point of access for user entities. Other auth domain services delegate
 * user retrieval and persistence here instead of using UserRepository directly,
 * keeping persistence coupling in one place and making the auth subdomains
 * easier to test and evolve independently.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ModelMapper modelMapper;
    private final SecurityAuditLogger securityAuditLogger;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public User getByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));
    }

    @Override
    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found: " + username));
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> findAllByTenantId(Long tenantId, Pageable pageable) {
        return userRepository.findAllByTenantId(tenantId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public User getByIdAndTenantId(Long id, Long tenantId) {
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUser(String username) {
        User user = getByUsername(username);
        UserProfileResponse response = modelMapper.map(user, UserProfileResponse.class);
        response.setRoles(user.getRoles().stream().map(Role::getName).toArray(String[]::new));
        return response;
    }

    @Override
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

        refreshTokenService.revokeAllUserRefreshTokens(user.getUsername());
        securityAuditLogger.logPasswordChanged(user.getUsername(), ipAddress);
    }

    @Override
    @Transactional
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public void delete(User user) {
        userRepository.delete(user);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRoleName(String roleName) {
        return userRepository.countByRolesName(roleName);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByRoleNameAndTenantId(String roleName, Long tenantId) {
        return userRepository.countByRolesNameAndTenantId(roleName, tenantId);
    }
}
