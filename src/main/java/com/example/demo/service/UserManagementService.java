package com.example.demo.service;

import com.example.demo.constants.AuditActions;
import com.example.demo.constants.Roles;
import com.example.demo.constants.UserPermission;
import com.example.demo.dto.UserCreateRequest;
import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserManagementResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import com.example.demo.entity.Department;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserManagementMapper;
import com.example.demo.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserService userService;
    private final RoleManagementService roleManagementService;
    private final TenantManagementService tenantManagementService;
    private final DepartmentManagementService departmentManagementService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final UserManagementMapper userManagementMapper;

    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserManagementResponse createUser(UserCreateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        String roleName = request.getRoleName().toUpperCase();
        Role role = roleManagementService.findByName(roleName);

        if (!canAssignBuiltInRole(currentUser, role)) {
            throw new IllegalArgumentException("Only admin can create users with role " + roleName);
        }

        Tenant tenant = resolveTenantForCreation(request.getTenantId(), currentUser);
        Department department = resolveDepartmentForCreation(request.getDepartmentId(), currentUser, tenant);

        if (userService.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userService.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .tenant(tenant)
                .department(department)
                .build();
        user.getRoles().add(role);

        User saved = userService.save(user);
        auditLogService.record(AuditActions.USER_CREATED, AuditActions.RESOURCE_USER,
                String.valueOf(saved.getId()), "User created with role " + roleName, currentUsername);
        return userManagementMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_READ')")
    public Page<UserManagementResponse> getUsers(Pageable pageable, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        if (authorizationService.isSuperAdmin(currentUser)) {
            return userService.findAll(pageable).map(userManagementMapper::toResponse);
        }
        if (currentUser.getTenant() == null) {
            return Page.empty(pageable);
        }
        return userService.findAllByTenantId(currentUser.getTenant().getId(), pageable)
                .map(userManagementMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_READ')")
    public UserManagementResponse getUserById(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);
        return userManagementMapper.toResponse(user);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserManagementResponse updateUser(Long id, UserUpdateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);

        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot update a user with more privileges");
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        User updated = userService.save(user);
        auditLogService.record(AuditActions.USER_UPDATED, AuditActions.RESOURCE_USER, String.valueOf(updated.getId()), "User updated", currentUsername);
        return userManagementMapper.toResponse(updated);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public void deleteUser(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);

        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        if (isLastAdmin(user)) {
            throw new IllegalArgumentException("Cannot delete the last admin");
        }
        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot delete a user with more privileges");
        }

        Long userId = user.getId();
        userService.delete(user);
        auditLogService.record(AuditActions.USER_DELETED, AuditActions.RESOURCE_USER, String.valueOf(userId), "User deleted", currentUsername);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_ENABLE')")
    public UserManagementResponse toggleUserEnabled(Long id, UserEnableRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);

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
        User saved = userService.save(user);
        String action = request.getEnabled() ? AuditActions.USER_ENABLED : AuditActions.USER_DISABLED;
        auditLogService.record(action, AuditActions.RESOURCE_USER, String.valueOf(saved.getId()), "User enabled state changed to " + request.getEnabled(), currentUsername);
        return userManagementMapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    public UserManagementResponse assignRole(Long id, UserRoleAssignmentRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);
        String roleName = request.getRoleName().toUpperCase();
        Role role = roleManagementService.findByName(roleName);

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
        User saved = userService.save(user);
        auditLogService.record(AuditActions.USER_ROLE_ASSIGNED, AuditActions.RESOURCE_USER, String.valueOf(saved.getId()), "Assigned role " + roleName, currentUsername);
        return userManagementMapper.toResponse(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    public UserManagementResponse removeRole(Long id, UserRoleAssignmentRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);
        String roleName = request.getRoleName().toUpperCase();
        Role role = roleManagementService.findByName(roleName);

        Set<UserPermission> granterPermissions = getAllPermissions(currentUser);

        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot modify roles of a user with more privileges");
        }
        if (!canAssignBuiltInRole(currentUser, role)) {
            throw new IllegalArgumentException("Only admin can remove this role");
        }
        if (Roles.ADMIN.equals(role.getName()) && isLastAdmin(user)) {
            throw new IllegalArgumentException("Cannot remove the last admin");
        }
        if (!user.getRoles().contains(role)) {
            throw new IllegalArgumentException("User does not have this role");
        }

        user.getRoles().remove(role);
        User saved = userService.save(user);
        auditLogService.record(AuditActions.USER_ROLE_REMOVED, AuditActions.RESOURCE_USER, String.valueOf(saved.getId()), "Removed role " + roleName, currentUsername);
        return userManagementMapper.toResponse(saved);
    }

    private User findAccessibleUser(Long id, User currentUser) {
        if (authorizationService.isSuperAdmin(currentUser)) {
            return userService.getById(id);
        }
        if (currentUser.getTenant() == null) {
            throw new AccessDeniedException("Cannot access this user");
        }
        return userService.getByIdAndTenantId(id, currentUser.getTenant().getId());
    }

    private boolean isLastAdmin(User user) {
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> Roles.ADMIN.equals(role.getName()));
        return isAdmin && userService.countByRoleName(Roles.ADMIN) <= 1;
    }

    private boolean canManage(User granter, User target) {
        if (!authorizationService.canAccessTenant(granter, target.getTenant())) {
            return false;
        }
        return getAllPermissions(granter).containsAll(getAllPermissions(target));
    }

    private boolean canAssignBuiltInRole(User granter, Role role) {
        if (!(Roles.ADMIN.equals(role.getName()) || Roles.USER_MANAGER.equals(role.getName()))) {
            return true;
        }
        return granter.getRoles().stream()
                .anyMatch(r -> Roles.ADMIN.equals(r.getName()));
    }

    private Set<UserPermission> getAllPermissions(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .collect(Collectors.toSet());
    }

    private Tenant resolveTenantForCreation(Long tenantId, User currentUser) {
        if (tenantId == null) {
            if (currentUser.getTenant() != null) {
                throw new IllegalArgumentException("Tenant is required to create a user in your tenant");
            }
            return null;
        }
        Tenant tenant = tenantManagementService.findById(tenantId);
        if (!authorizationService.canAccessTenant(currentUser, tenant)) {
            throw new AccessDeniedException("Cannot create users in this tenant");
        }
        return tenant;
    }

    private Department resolveDepartmentForCreation(Long departmentId, User currentUser, Tenant tenant) {
        if (departmentId == null) {
            return null;
        }
        Department department = departmentManagementService.findById(departmentId);
        if (tenant != null && !tenant.getId().equals(department.getTenant().getId())) {
            throw new IllegalArgumentException("Department must belong to the same tenant");
        }
        if (tenant == null && !authorizationService.isSuperAdmin(currentUser)) {
            throw new AccessDeniedException("Cannot assign a department without a tenant");
        }
        return department;
    }
}
