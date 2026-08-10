package com.example.demo.service;

import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserManagementResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.entity.UserPermission;
import com.example.demo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserService userService;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_READ')")
    public List<UserManagementResponse> getUsers() {
        return userService.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_READ')")
    public UserManagementResponse getUserById(Long id) {
        return mapToResponse(userService.getById(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserManagementResponse updateUser(Long id, UserUpdateRequest request, String currentUsername) {
        User user = userService.getById(id);
        User currentUser = userService.getByUsername(currentUsername);

        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot update a user with more privileges");
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        return mapToResponse(userService.save(user));
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public void deleteUser(Long id, String currentUsername) {
        User user = userService.getById(id);
        User currentUser = userService.getByUsername(currentUsername);

        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        if (isLastAdmin(user)) {
            throw new IllegalArgumentException("Cannot delete the last admin");
        }
        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot delete a user with more privileges");
        }

        userService.delete(user);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_ENABLE')")
    public UserManagementResponse toggleUserEnabled(Long id, UserEnableRequest request, String currentUsername) {
        User user = userService.getById(id);
        User currentUser = userService.getByUsername(currentUsername);

        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalArgumentException("Cannot change your own enabled state");
        }
        if (!request.getEnabled() && isLastAdmin(user)) {
            throw new IllegalArgumentException("Cannot disable the last admin");
        }
        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot change enabled state of a user with more privileges");
        }

        user.setEnabled(request.getEnabled());
        return mapToResponse(userService.save(user));
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    public UserManagementResponse assignRole(Long id, UserRoleAssignmentRequest request, String currentUsername) {
        User user = userService.getById(id);
        String roleName = request.getRoleName().toUpperCase();
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        User currentUser = userService.getByUsername(currentUsername);
        Set<UserPermission> granterPermissions = getAllPermissions(currentUser);

        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot modify roles of a user with more privileges");
        }
        if (!canAssignBuiltInRole(currentUser, role)) {
            throw new IllegalArgumentException("Only admin can assign this role");
        }
        if (!granterPermissions.containsAll(role.getPermissions())) {
            throw new IllegalArgumentException("Cannot assign a role with permissions you do not have");
        }
        if (user.getRoles().contains(role)) {
            throw new IllegalArgumentException("User already has this role");
        }

        user.getRoles().add(role);
        return mapToResponse(userService.save(user));
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    public UserManagementResponse removeRole(Long id, UserRoleAssignmentRequest request, String currentUsername) {
        User user = userService.getById(id);
        String roleName = request.getRoleName().toUpperCase();
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        User currentUser = userService.getByUsername(currentUsername);
        Set<UserPermission> granterPermissions = getAllPermissions(currentUser);

        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot modify roles of a user with more privileges");
        }
        if (!canAssignBuiltInRole(currentUser, role)) {
            throw new IllegalArgumentException("Only admin can remove this role");
        }
        if ("ADMIN".equals(role.getName()) && isLastAdmin(user)) {
            throw new IllegalArgumentException("Cannot remove the last admin");
        }
        if (!user.getRoles().contains(role)) {
            throw new IllegalArgumentException("User does not have this role");
        }

        user.getRoles().remove(role);
        return mapToResponse(userService.save(user));
    }

    private boolean isLastAdmin(User user) {
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));
        return isAdmin && userService.countByRoleName("ADMIN") <= 1;
    }

    private boolean canManage(User granter, User target) {
        return getAllPermissions(granter).containsAll(getAllPermissions(target));
    }

    private boolean canAssignBuiltInRole(User granter, Role role) {
        if (!("ADMIN".equals(role.getName()) || "USER_MANAGER".equals(role.getName()))) {
            return true;
        }
        return granter.getRoles().stream()
                .anyMatch(r -> "ADMIN".equals(r.getName()));
    }

    private Set<UserPermission> getAllPermissions(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());
    }

    private UserManagementResponse mapToResponse(User user) {
        return UserManagementResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(user.getEnabled())
                .accountNonLocked(user.getAccountNonLocked())
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .permissions(getAllPermissions(user).stream().map(UserPermission::name).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
