package com.example.demo.service.impl;

import com.example.demo.constants.AuditActions;
import com.example.demo.constants.Roles;
import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.dto.UserCreateRequest;
import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserMfaToggleRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import com.example.demo.entity.Department;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserManagementMapper;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.security.service.ClientIpResolver;
import com.example.demo.service.AuditLogService;
import com.example.demo.service.DepartmentManagementService;
import com.example.demo.service.RoleManagementService;
import com.example.demo.service.TenantManagementService;
import com.example.demo.service.UserManagementService;
import com.example.demo.service.MfaSetupService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final UserService userService;
    private final RoleManagementService roleManagementService;
    private final TenantManagementService tenantManagementService;
    private final DepartmentManagementService departmentManagementService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final UserManagementMapper userManagementMapper;
    private final PasswordEncoder passwordEncoder;
    private final MfaSetupService mfaSetupService;
    private final ClientIpResolver clientIpResolver;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public UserResponse createUser(UserCreateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);

        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        if (userService.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userService.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        Tenant tenant = resolveTenantForCreation(currentUser, request.getTenantId());
        Department department = resolveDepartmentForCreation(currentUser, tenant, request.getDepartmentId());

        String roleName = request.getRoleName() == null || request.getRoleName().isBlank()
                ? Roles.EMPLOYEE : request.getRoleName().toUpperCase();
        Role role = roleManagementService.findByName(roleName);

        validateRoleAssignment(currentUser, role);

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .tenant(tenant)
                .department(department)
                .build();
        user.getRoles().add(role);

        User saved;
        try {
            saved = userService.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Username or email already exists");
        }
        auditLogService.record(AuditActions.USER_CREATED, AuditActions.RESOURCE_USER,
                String.valueOf(saved.getId()), "User created with role " + roleName, currentUsername);
        return userManagementMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_READ')")
    public Page<UserResponse> getUsers(Pageable pageable, String currentUsername) {
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

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_READ')")
    public UserResponse getUserById(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);
        return userManagementMapper.toResponse(user);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public UserResponse updateUser(Long id, UserUpdateRequest request, String currentUsername) {
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
        if (request.getDepartmentId() != null) {
            Department department = departmentManagementService.findById(request.getDepartmentId());
            if (user.getTenant() == null || !user.getTenant().getId().equals(department.getTenant().getId())) {
                throw new IllegalArgumentException("Department must belong to the same tenant");
            }
            user.setDepartment(department);
        }

        User updated = userService.save(user);
        auditLogService.record(AuditActions.USER_UPDATED, AuditActions.RESOURCE_USER, String.valueOf(updated.getId()), "User updated", currentUsername);
        return userManagementMapper.toResponse(updated);
    }

    @Override
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

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_ENABLE')")
    public UserResponse toggleUserEnabled(Long id, UserEnableRequest request, String currentUsername) {
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

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    public UserResponse assignRole(Long id, UserRoleAssignmentRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);
        String roleName = request.getRoleName().toUpperCase();
        Role role = roleManagementService.findByName(roleName);

        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot modify roles of a user with more privileges");
        }
        validateRoleAssignment(currentUser, role);
        if (user.getRoles().contains(role)) {
            throw new IllegalArgumentException("User already has this role");
        }

        user.getRoles().add(role);
        User saved = userService.save(user);
        auditLogService.record(AuditActions.USER_ROLE_ASSIGNED, AuditActions.RESOURCE_USER, String.valueOf(saved.getId()), "Assigned role " + roleName, currentUsername);
        return userManagementMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_ASSIGN_ROLE')")
    public UserResponse removeRole(Long id, UserRoleAssignmentRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User user = findAccessibleUser(id, currentUser);
        String roleName = request.getRoleName().toUpperCase();
        Role role = roleManagementService.findByName(roleName);

        if (!canManage(currentUser, user)) {
            throw new IllegalArgumentException("Cannot modify roles of a user with more privileges");
        }
        if (!canAssignBuiltInRole(currentUser, role)) {
            throw new IllegalArgumentException("Only admin can remove this role");
        }
        if (!UserManagementMapper.getAllPermissions(currentUser).containsAll(role.getPermissions())) {
            throw new IllegalArgumentException("Cannot remove a role with permissions you do not have");
        }
        if ((Roles.PLATFORM_ADMIN.equals(role.getName()) || Roles.TENANT_ADMIN.equals(role.getName()))
                && isLastAdmin(user)) {
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

    private void validateRoleAssignment(User granter, Role role) {
        if (!canAssignBuiltInRole(granter, role)) {
            throw new IllegalArgumentException("Only admin can assign this role");
        }
        if (!UserManagementMapper.getAllPermissions(granter).containsAll(role.getPermissions())) {
            throw new IllegalArgumentException("Cannot assign a role with permissions you do not have");
        }
    }

    private Tenant resolveTenantForCreation(User creator, Long tenantId) {
        if (tenantId == null) {
            return creator.getTenant();
        }
        if (!authorizationService.isSuperAdmin(creator)) {
            throw new AccessDeniedException("Cannot create user in a different tenant");
        }
        return tenantManagementService.findById(tenantId);
    }

    private Department resolveDepartmentForCreation(User creator, Tenant tenant, Long departmentId) {
        Department department = departmentManagementService.findById(departmentId);
        if (tenant == null || !tenant.getId().equals(department.getTenant().getId())) {
            throw new IllegalArgumentException("Department must belong to the same tenant");
        }
        return department;
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
                .anyMatch(role -> Roles.PLATFORM_ADMIN.equals(role.getName())
                        || Roles.TENANT_ADMIN.equals(role.getName()));
        if (!isAdmin) {
            return false;
        }
        if (user.getTenant() == null) {
            return (userService.countByRoleName(Roles.PLATFORM_ADMIN)
                    + userService.countByRoleName(Roles.TENANT_ADMIN)) <= 1;
        }
        return (userService.countByRoleNameAndTenantId(Roles.PLATFORM_ADMIN, user.getTenant().getId())
                + userService.countByRoleNameAndTenantId(Roles.TENANT_ADMIN, user.getTenant().getId())) <= 1;
    }

    private boolean canManage(User granter, User target) {
        if (!authorizationService.canAccessTenant(granter, target.getTenant())) {
            return false;
        }
        return UserManagementMapper.getAllPermissions(granter).containsAll(UserManagementMapper.getAllPermissions(target));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public MfaSetupResponse enableMfa(Long id, UserMfaToggleRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User targetUser = findAccessibleUser(id, currentUser);

        if (!canManage(currentUser, targetUser)) {
            throw new IllegalArgumentException("Cannot manage MFA for a user with more privileges");
        }

        MfaSetupResponse response = mfaSetupService.enableMfa(targetUser, request.getMethod(), getClientIp());
        auditLogService.record(AuditActions.USER_MFA_ENABLED, AuditActions.RESOURCE_USER,
                String.valueOf(targetUser.getId()), "MFA enabled with method " + request.getMethod(), currentUsername);
        return response;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public void disableMfa(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User targetUser = findAccessibleUser(id, currentUser);

        if (!canManage(currentUser, targetUser)) {
            throw new IllegalArgumentException("Cannot manage MFA for a user with more privileges");
        }

        mfaSetupService.disableMfa(targetUser, getClientIp());
        auditLogService.record(AuditActions.USER_MFA_DISABLED, AuditActions.RESOURCE_USER,
                String.valueOf(targetUser.getId()), "MFA disabled", currentUsername);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('USER_WRITE')")
    public MfaSetupResponse resetMfa(Long id, UserMfaToggleRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        User targetUser = findAccessibleUser(id, currentUser);

        if (!canManage(currentUser, targetUser)) {
            throw new IllegalArgumentException("Cannot manage MFA for a user with more privileges");
        }

        MfaSetupResponse response = mfaSetupService.resetMfa(targetUser, request.getMethod(), getClientIp());
        auditLogService.record(AuditActions.USER_MFA_RESET, AuditActions.RESOURCE_USER,
                String.valueOf(targetUser.getId()), "MFA reset with method " + request.getMethod(), currentUsername);
        return response;
    }

    private String getClientIp() {
        try {
            var attrs = (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            return clientIpResolver.resolveClientIp(attrs.getRequest());
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean canAssignBuiltInRole(User granter, Role role) {
        if (!(Roles.PLATFORM_ADMIN.equals(role.getName())
                || Roles.TENANT_ADMIN.equals(role.getName())
                || Roles.USER_MANAGER.equals(role.getName()))) {
            return true;
        }
        return granter.getRoles().stream()
                .anyMatch(r -> Roles.PLATFORM_ADMIN.equals(r.getName())
                        || Roles.TENANT_ADMIN.equals(r.getName()));
    }
}
