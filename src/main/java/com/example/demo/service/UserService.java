package com.example.demo.service;

import com.example.demo.dto.ChangePasswordRequest;
import com.example.demo.dto.UserProfileResponse;
import com.example.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserService {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    User getByUsernameOrEmail(String usernameOrEmail);

    User getByUsername(String username);

    User getById(Long id);

    Page<User> findAll(Pageable pageable);

    Page<User> findAllByTenantId(Long tenantId, Pageable pageable);

    User getByIdAndTenantId(Long id, Long tenantId);

    Optional<User> findByEmail(String email);

    UserProfileResponse getCurrentUser(String username);

    void changePassword(String username, ChangePasswordRequest request, String ipAddress);

    User save(User user);

    void delete(User user);

    long countByRoleName(String roleName);

    long countByRoleNameAndTenantId(String roleName, Long tenantId);
}
