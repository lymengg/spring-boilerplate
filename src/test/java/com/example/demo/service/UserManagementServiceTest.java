package com.example.demo.service;

import com.example.demo.constants.AuditActions;
import com.example.demo.constants.Roles;
import com.example.demo.dto.MfaSetupResponse;
import com.example.demo.dto.PageResponse;
import com.example.demo.dto.UserCreateRequest;
import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserMfaToggleRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import com.example.demo.entity.Department;
import com.example.demo.entity.MfaMethod;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserManagementMapper;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.security.service.ClientIpResolver;
import com.example.demo.service.impl.UserManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private RoleManagementService roleManagementService;

    @Mock
    private TenantManagementService tenantManagementService;

    @Mock
    private DepartmentManagementService departmentManagementService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserManagementMapper userManagementMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MfaSetupService mfaSetupService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @InjectMocks
    private UserManagementServiceImpl userManagementService;

    private Role platformAdminRole;
    private Role tenantAdminRole;
    private Role userManagerRole;
    private Role employeeRole;
    private Tenant tenant;
    private Department department;
    private User superAdmin;
    private User tenantAdmin;
    private User manager;
    private User employee;

    @BeforeEach
    void setUp() {
        platformAdminRole = role(Roles.PLATFORM_ADMIN);
        tenantAdminRole = role(Roles.TENANT_ADMIN);
        userManagerRole = role(Roles.USER_MANAGER);
        employeeRole = role(Roles.EMPLOYEE);

        tenant = Tenant.builder().id(1L).name("Acme Corp").build();
        department = Department.builder().id(1L).name("Engineering").tenant(tenant).build();

        superAdmin = userWithRole("superadmin", null, platformAdminRole);
        tenantAdmin = userWithRole("tenantadmin", tenant, tenantAdminRole);
        manager = userWithRole("manager", tenant, userManagerRole);
        employee = userWithRole("employee", tenant, employeeRole);
    }

    @Test
    @DisplayName("Creating a user with default role saves and audits")
    void createUserWithDefaultRole() {
        UserCreateRequest request = UserCreateRequest.builder()
                .username(" jane.doe ")
                .email(" JANE@EXAMPLE.COM ")
                .password("Password123!")
                .firstName("Jane")
                .lastName("Doe")
                .departmentId(1L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.existsByUsername("jane.doe")).thenReturn(false);
        when(userService.existsByEmail("jane@example.com")).thenReturn(false);
        when(departmentManagementService.findById(1L)).thenReturn(department);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded");
        when(userService.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(userManagementMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        userManagementService.createUser(request, "manager");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("jane.doe");
        assertThat(saved.getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getPassword()).isEqualTo("encoded");
        assertThat(saved.getTenant()).isEqualTo(tenant);
        assertThat(saved.getDepartment()).isEqualTo(department);
        assertThat(saved.getRoles()).containsExactly(employeeRole);
        verify(auditLogService).record(AuditActions.USER_CREATED, AuditActions.RESOURCE_USER,
                "10", "User created with role EMPLOYEE", "manager");
    }

    @Test
    @DisplayName("Creating a user with a duplicate username fails")
    void createUserDuplicateUsernameFails() {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("jane.doe")
                .email("jane@example.com")
                .password("Password123!")
                .departmentId(1L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.existsByUsername("jane.doe")).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.createUser(request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    @DisplayName("Creating a user with a duplicate email fails")
    void createUserDuplicateEmailFails() {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("jane.doe")
                .email("jane@example.com")
                .password("Password123!")
                .departmentId(1L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.existsByUsername("jane.doe")).thenReturn(false);
        when(userService.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.createUser(request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
    }

    @Test
    @DisplayName("Non-super-admin cannot create a user in another tenant")
    void nonSuperAdminCannotCreateInOtherTenant() {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("jane.doe")
                .email("jane@example.com")
                .password("Password123!")
                .tenantId(2L)
                .departmentId(1L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.existsByUsername("jane.doe")).thenReturn(false);
        when(userService.existsByEmail("jane@example.com")).thenReturn(false);
        when(authorizationService.isSuperAdmin(manager)).thenReturn(false);

        assertThatThrownBy(() -> userManagementService.createUser(request, "manager"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cannot create user in a different tenant");
    }

    @Test
    @DisplayName("Super admin can create a user in a specific tenant")
    void superAdminCanCreateInSpecificTenant() {
        Tenant otherTenant = Tenant.builder().id(2L).name("Other Corp").build();
        Department otherDept = Department.builder().id(2L).name("Other Dept").tenant(otherTenant).build();
        UserCreateRequest request = UserCreateRequest.builder()
                .username("jane.doe")
                .email("jane@example.com")
                .password("Password123!")
                .tenantId(2L)
                .departmentId(2L)
                .build();

        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(userService.existsByUsername("jane.doe")).thenReturn(false);
        when(userService.existsByEmail("jane@example.com")).thenReturn(false);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        when(tenantManagementService.findById(2L)).thenReturn(otherTenant);
        when(departmentManagementService.findById(2L)).thenReturn(otherDept);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(userService.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userManagementMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        userManagementService.createUser(request, "superadmin");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        assertThat(captor.getValue().getTenant()).isEqualTo(otherTenant);
    }

    @Test
    @DisplayName("Creating a user with a department in another tenant fails")
    void createUserDepartmentTenantMismatchFails() {
        Tenant otherTenant = Tenant.builder().id(2L).name("Other Corp").build();
        Department otherDept = Department.builder().id(2L).name("Other Dept").tenant(otherTenant).build();
        UserCreateRequest request = UserCreateRequest.builder()
                .username("jane.doe")
                .email("jane@example.com")
                .password("Password123!")
                .departmentId(2L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.existsByUsername("jane.doe")).thenReturn(false);
        when(userService.existsByEmail("jane@example.com")).thenReturn(false);
        when(departmentManagementService.findById(2L)).thenReturn(otherDept);

        assertThatThrownBy(() -> userManagementService.createUser(request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Department must belong to the same tenant");
    }

    @Test
    @DisplayName("Non-admin cannot create a user with an admin role")
    void nonAdminCannotCreateWithAdminRole() {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("jane.doe")
                .email("jane@example.com")
                .password("Password123!")
                .roleName(Roles.PLATFORM_ADMIN)
                .departmentId(1L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.existsByUsername("jane.doe")).thenReturn(false);
        when(userService.existsByEmail("jane@example.com")).thenReturn(false);
        when(departmentManagementService.findById(1L)).thenReturn(department);
        when(roleManagementService.findByName(Roles.PLATFORM_ADMIN)).thenReturn(platformAdminRole);

        assertThatThrownBy(() -> userManagementService.createUser(request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only admin can assign this role");
    }

    @Test
    @DisplayName("Data integrity violation on save maps to a friendly message")
    void createUserDataIntegrityViolationMapsToFriendlyMessage() {
        UserCreateRequest request = UserCreateRequest.builder()
                .username("jane.doe")
                .email("jane@example.com")
                .password("Password123!")
                .departmentId(1L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.existsByUsername("jane.doe")).thenReturn(false);
        when(userService.existsByEmail("jane@example.com")).thenReturn(false);
        when(departmentManagementService.findById(1L)).thenReturn(department);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(userService.save(any(User.class))).thenThrow(new DataIntegrityViolationException("constraint"));

        assertThatThrownBy(() -> userManagementService.createUser(request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username or email already exists");
    }

    @Test
    @DisplayName("Super admin can list all users")
    void superAdminCanListAllUsers() {
        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        PageRequest pageable = PageRequest.of(0, 10);
        when(userService.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(employee), pageable, 1));
        when(userManagementMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        PageResponse<UserResponse> result = userManagementService.getUsers(pageable, "superadmin");

        assertThat(result.getContent()).hasSize(1);
        verify(userService).findAll(pageable);
    }

    @Test
    @DisplayName("Tenant user can list only users in their tenant")
    void tenantUserListsOnlyOwnTenant() {
        when(userService.getByUsername("manager")).thenReturn(manager);
        when(authorizationService.isSuperAdmin(manager)).thenReturn(false);
        PageRequest pageable = PageRequest.of(0, 10);
        when(userService.findAllByTenantId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(employee), pageable, 1));
        when(userManagementMapper.toResponse(any(User.class))).thenReturn(UserResponse.builder().build());

        PageResponse<UserResponse> result = userManagementService.getUsers(pageable, "manager");

        assertThat(result.getContent()).hasSize(1);
        verify(userService).findAllByTenantId(1L, pageable);
    }

    @Test
    @DisplayName("Tenant-less user gets an empty page")
    void tenantLessUserGetsEmptyPage() {
        User noTenant = userWithRole("notenant", null, employeeRole);
        when(userService.getByUsername("notenant")).thenReturn(noTenant);
        when(authorizationService.isSuperAdmin(noTenant)).thenReturn(false);
        PageRequest pageable = PageRequest.of(0, 10);

        PageResponse<UserResponse> result = userManagementService.getUsers(pageable, "notenant");

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Super admin can get any user by id")
    void superAdminCanGetUserById() {
        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        when(userService.getById(5L)).thenReturn(employee);
        when(userManagementMapper.toResponse(employee)).thenReturn(UserResponse.builder().build());

        userManagementService.getUserById(5L, "superadmin");

        verify(userService).getById(5L);
    }

    @Test
    @DisplayName("Tenant user can get a user in their tenant")
    void tenantUserCanGetUserInOwnTenant() {
        when(userService.getByUsername("manager")).thenReturn(manager);
        when(authorizationService.isSuperAdmin(manager)).thenReturn(false);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(userManagementMapper.toResponse(employee)).thenReturn(UserResponse.builder().build());

        userManagementService.getUserById(5L, "manager");

        verify(userService).getByIdAndTenantId(5L, 1L);
    }

    @Test
    @DisplayName("Tenant-less user cannot access other users")
    void tenantLessUserCannotAccessUsers() {
        User noTenant = userWithRole("notenant", null, employeeRole);
        when(userService.getByUsername("notenant")).thenReturn(noTenant);
        when(authorizationService.isSuperAdmin(noTenant)).thenReturn(false);

        assertThatThrownBy(() -> userManagementService.getUserById(5L, "notenant"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cannot access this user");
    }

    @Test
    @DisplayName("Updating a user saves changes and audits")
    void updateUserSavesAndAudits() {
        UserUpdateRequest request = UserUpdateRequest.builder()
                .firstName("Jane")
                .departmentId(1L)
                .build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(departmentManagementService.findById(1L)).thenReturn(department);
        when(userService.save(employee)).thenReturn(employee);
        when(userManagementMapper.toResponse(employee)).thenReturn(UserResponse.builder().build());

        userManagementService.updateUser(5L, request, "manager");

        assertThat(employee.getFirstName()).isEqualTo("Jane");
        verify(userService).save(employee);
        verify(auditLogService).record(AuditActions.USER_UPDATED, AuditActions.RESOURCE_USER,
                "5", "User updated", "manager");
    }

    @Test
    @DisplayName("Cannot update a user with more privileges")
    void cannotUpdateUserWithMorePrivileges() {
        UserUpdateRequest request = UserUpdateRequest.builder().firstName("Hacked").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(tenantAdmin);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.updateUser(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update a user with more privileges");
    }

    @Test
    @DisplayName("Cannot reassign a user to a department in another tenant")
    void cannotReassignToOtherTenantDepartment() {
        Tenant otherTenant = Tenant.builder().id(2L).name("Other Corp").build();
        Department otherDept = Department.builder().id(2L).name("Other Dept").tenant(otherTenant).build();
        UserUpdateRequest request = UserUpdateRequest.builder().departmentId(2L).build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(departmentManagementService.findById(2L)).thenReturn(otherDept);

        assertThatThrownBy(() -> userManagementService.updateUser(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Department must belong to the same tenant");
    }

    @Test
    @DisplayName("Deleting a user audits the action")
    void deleteUserAuditsAction() {
        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        userManagementService.deleteUser(5L, "manager");

        verify(userService).delete(employee);
        verify(auditLogService).record(AuditActions.USER_DELETED, AuditActions.RESOURCE_USER,
                "5", "User deleted", "manager");
    }

    @Test
    @DisplayName("Cannot delete your own account")
    void cannotDeleteOwnAccount() {
        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(manager);

        assertThatThrownBy(() -> userManagementService.deleteUser(5L, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete your own account");
    }

    @Test
    @DisplayName("Cannot delete the last admin")
    void cannotDeleteLastAdmin() {
        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        when(userService.getById(5L)).thenReturn(tenantAdmin);
        when(userService.countByRoleNameAndTenantId(Roles.PLATFORM_ADMIN, 1L)).thenReturn(0L);
        when(userService.countByRoleNameAndTenantId(Roles.TENANT_ADMIN, 1L)).thenReturn(1L);

        assertThatThrownBy(() -> userManagementService.deleteUser(5L, "superadmin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete the last admin");
    }

    @Test
    @DisplayName("Cannot delete a user with more privileges")
    void cannotDeleteUserWithMorePrivileges() {
        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(tenantAdmin);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(userService.countByRoleNameAndTenantId(Roles.PLATFORM_ADMIN, 1L)).thenReturn(5L);
        when(userService.countByRoleNameAndTenantId(Roles.TENANT_ADMIN, 1L)).thenReturn(5L);

        assertThatThrownBy(() -> userManagementService.deleteUser(5L, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete a user with more privileges");
    }

    @Test
    @DisplayName("Enabling a user saves and audits")
    void enableUserSavesAndAudits() {
        UserEnableRequest request = UserEnableRequest.builder().enabled(true).build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(userService.save(employee)).thenReturn(employee);
        when(userManagementMapper.toResponse(employee)).thenReturn(UserResponse.builder().build());

        userManagementService.toggleUserEnabled(5L, request, "manager");

        assertThat(employee.getEnabled()).isTrue();
        verify(auditLogService).record(AuditActions.USER_ENABLED, AuditActions.RESOURCE_USER,
                "5", "User enabled state changed to true", "manager");
    }

    @Test
    @DisplayName("Cannot change your own enabled state")
    void cannotChangeOwnEnabledState() {
        UserEnableRequest request = UserEnableRequest.builder().enabled(false).build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(manager);

        assertThatThrownBy(() -> userManagementService.toggleUserEnabled(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change your own enabled state");
    }

    @Test
    @DisplayName("Cannot disable the last admin")
    void cannotDisableLastAdmin() {
        UserEnableRequest request = UserEnableRequest.builder().enabled(false).build();

        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        when(userService.getById(5L)).thenReturn(tenantAdmin);
        when(userService.countByRoleNameAndTenantId(Roles.PLATFORM_ADMIN, 1L)).thenReturn(0L);
        when(userService.countByRoleNameAndTenantId(Roles.TENANT_ADMIN, 1L)).thenReturn(1L);

        assertThatThrownBy(() -> userManagementService.toggleUserEnabled(5L, request, "superadmin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot disable the last admin");
    }

    @Test
    @DisplayName("Cannot change enabled state of a user with more privileges")
    void cannotChangeEnabledStateOfMorePrivilegedUser() {
        UserEnableRequest request = UserEnableRequest.builder().enabled(false).build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(tenantAdmin);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(userService.countByRoleNameAndTenantId(Roles.PLATFORM_ADMIN, 1L)).thenReturn(5L);
        when(userService.countByRoleNameAndTenantId(Roles.TENANT_ADMIN, 1L)).thenReturn(5L);

        assertThatThrownBy(() -> userManagementService.toggleUserEnabled(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot change enabled state of a user with more privileges");
    }

    @Test
    @DisplayName("Assigning a role saves and audits")
    void assignRoleSavesAndAudits() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("EMPLOYEE").build();
        User noRoleUser = userWithRole("norole", tenant, employeeRole);
        noRoleUser.getRoles().clear();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(noRoleUser);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(userService.save(noRoleUser)).thenReturn(noRoleUser);
        when(userManagementMapper.toResponse(noRoleUser)).thenReturn(UserResponse.builder().build());

        userManagementService.assignRole(5L, request, "manager");

        assertThat(noRoleUser.getRoles()).contains(employeeRole);
        verify(auditLogService).record(AuditActions.USER_ROLE_ASSIGNED, AuditActions.RESOURCE_USER,
                "5", "Assigned role EMPLOYEE", "manager");
    }

    @Test
    @DisplayName("Cannot assign a role to a user with more privileges")
    void cannotAssignRoleToMorePrivilegedUser() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("EMPLOYEE").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(tenantAdmin);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.assignRole(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify roles of a user with more privileges");
    }

    @Test
    @DisplayName("Non-admin cannot assign a built-in role")
    void nonAdminCannotAssignBuiltInRole() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("USER_MANAGER").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(roleManagementService.findByName(Roles.USER_MANAGER)).thenReturn(userManagerRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.assignRole(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only admin can assign this role");
    }

    @Test
    @DisplayName("Assigning a role the user already has fails")
    void assignExistingRoleFails() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("EMPLOYEE").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.assignRole(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User already has this role");
    }

    @Test
    @DisplayName("Removing a role saves and audits")
    void removeRoleSavesAndAudits() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("EMPLOYEE").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(userService.save(employee)).thenReturn(employee);
        when(userManagementMapper.toResponse(employee)).thenReturn(UserResponse.builder().build());

        userManagementService.removeRole(5L, request, "manager");

        assertThat(employee.getRoles()).doesNotContain(employeeRole);
        verify(auditLogService).record(AuditActions.USER_ROLE_REMOVED, AuditActions.RESOURCE_USER,
                "5", "Removed role EMPLOYEE", "manager");
    }

    @Test
    @DisplayName("Cannot remove a role from a user with more privileges")
    void cannotRemoveRoleFromMorePrivilegedUser() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("EMPLOYEE").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(tenantAdmin);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.removeRole(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot modify roles of a user with more privileges");
    }

    @Test
    @DisplayName("Cannot remove the last admin role")
    void cannotRemoveLastAdminRole() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("TENANT_ADMIN").build();

        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);
        when(userService.getById(5L)).thenReturn(tenantAdmin);
        when(roleManagementService.findByName(Roles.TENANT_ADMIN)).thenReturn(tenantAdminRole);
        when(authorizationService.canAccessTenant(superAdmin, tenant)).thenReturn(true);
        when(userService.countByRoleNameAndTenantId(Roles.PLATFORM_ADMIN, 1L)).thenReturn(0L);
        when(userService.countByRoleNameAndTenantId(Roles.TENANT_ADMIN, 1L)).thenReturn(1L);

        assertThatThrownBy(() -> userManagementService.removeRole(5L, request, "superadmin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot remove the last admin");
    }

    @Test
    @DisplayName("Removing a role the user does not have fails")
    void removeMissingRoleFails() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("EMPLOYEE").build();
        User noRoleUser = userWithRole("norole", tenant, employeeRole);
        noRoleUser.getRoles().clear();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(noRoleUser);
        when(roleManagementService.findByName(Roles.EMPLOYEE)).thenReturn(employeeRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.removeRole(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User does not have this role");
    }

    @Test
    @DisplayName("Only admin can remove a built-in role")
    void onlyAdminCanRemoveBuiltInRole() {
        UserRoleAssignmentRequest request = UserRoleAssignmentRequest.builder().roleName("USER_MANAGER").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(roleManagementService.findByName(Roles.USER_MANAGER)).thenReturn(userManagerRole);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.removeRole(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only admin can remove this role");
    }

    @Test
    @DisplayName("Enabling MFA delegates and audits")
    void enableMfaDelegatesAndAudits() {
        UserMfaToggleRequest request = UserMfaToggleRequest.builder().method(MfaMethod.TOTP).build();
        MfaSetupResponse response = MfaSetupResponse.builder()
                .method("TOTP").secret("SECRET").qrUri("otpauth://...").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(mfaSetupService.enableMfa(employee, MfaMethod.TOTP, "unknown")).thenReturn(response);

        MfaSetupResponse result = userManagementService.enableMfa(5L, request, "manager");

        assertThat(result).isEqualTo(response);
        verify(auditLogService).record(AuditActions.USER_MFA_ENABLED, AuditActions.RESOURCE_USER,
                "5", "MFA enabled with method TOTP", "manager");
    }

    @Test
    @DisplayName("Cannot manage MFA for a user with more privileges")
    void cannotManageMfaForMorePrivilegedUser() {
        UserMfaToggleRequest request = UserMfaToggleRequest.builder().method(MfaMethod.TOTP).build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(tenantAdmin);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.enableMfa(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot manage MFA for a user with more privileges");
    }

    @Test
    @DisplayName("Disabling MFA delegates and audits")
    void disableMfaDelegatesAndAudits() {
        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        userManagementService.disableMfa(5L, "manager");

        verify(mfaSetupService).disableMfa(employee, "unknown");
        verify(auditLogService).record(AuditActions.USER_MFA_DISABLED, AuditActions.RESOURCE_USER,
                "5", "MFA disabled", "manager");
    }

    @Test
    @DisplayName("Resetting MFA delegates and audits")
    void resetMfaDelegatesAndAudits() {
        UserMfaToggleRequest request = UserMfaToggleRequest.builder().method(MfaMethod.TOTP).build();
        MfaSetupResponse response = MfaSetupResponse.builder()
                .method("TOTP").secret("NEWSECRET").qrUri("otpauth://...").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(employee);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);
        when(mfaSetupService.resetMfa(employee, MfaMethod.TOTP, "unknown")).thenReturn(response);

        MfaSetupResponse result = userManagementService.resetMfa(5L, request, "manager");

        assertThat(result).isEqualTo(response);
        verify(auditLogService).record(AuditActions.USER_MFA_RESET, AuditActions.RESOURCE_USER,
                "5", "MFA reset with method TOTP", "manager");
    }

    @Test
    @DisplayName("Manager cannot manage another manager (privilege guard)")
    void managerCannotManageAnotherManager() {
        User otherManager = userWithRole("othermanager", tenant, userManagerRole);
        UserUpdateRequest request = UserUpdateRequest.builder().firstName("Hacked").build();

        when(userService.getByUsername("manager")).thenReturn(manager);
        when(userService.getByIdAndTenantId(5L, 1L)).thenReturn(otherManager);
        when(authorizationService.canAccessTenant(manager, tenant)).thenReturn(true);

        assertThatThrownBy(() -> userManagementService.updateUser(5L, request, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update a user with more privileges");
    }

    private Role role(String name) {
        return Role.builder().name(name).permissions(new HashSet<>()).build();
    }

    private User userWithRole(String username, Tenant tenant, Role role) {
        User user = User.builder()
                .id(5L)
                .username(username)
                .email(username + "@example.com")
                .password("secret")
                .enabled(true)
                .accountNonLocked(true)
                .tenant(tenant)
                .build();
        user.setRoles(new HashSet<>(Set.of(role)));
        return user;
    }
}
