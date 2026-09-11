package com.example.demo.service;

import com.example.demo.constants.Roles;
import com.example.demo.constants.UserPermission;
import com.example.demo.dto.PageResponse;
import com.example.demo.dto.RoleCreateRequest;
import com.example.demo.dto.RolePermissionRequest;
import com.example.demo.dto.RoleResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.RoleMapper;
import com.example.demo.repository.RoleRepository;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.impl.RoleManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleManagementServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserService userService;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private AuthorizationService authorizationService;

    @InjectMocks
    private RoleManagementServiceImpl roleManagementService;

    private Role customRole;
    private Role builtInRole;
    private Role otherRole;
    private RoleResponse stubResponse;
    private User superAdmin;
    private User tenantAdmin;

    @BeforeEach
    void setUp() {
        customRole = Role.builder().id(1L).name("CUSTOM_ROLE").build();
        builtInRole = Role.builder().id(2L).name(Roles.EMPLOYEE).build();
        otherRole = Role.builder().id(3L).name("OTHER_ROLE").build();
        stubResponse = RoleResponse.builder().id(1L).name("CUSTOM_ROLE").build();
        superAdmin = User.builder().email("superadmin@example.com").build();
        tenantAdmin = User.builder().email("tenantadmin@example.com")
                .tenant(Tenant.builder().id(1L).name("Acme Corp").build())
                .build();

        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleMapper.toResponse(any(Role.class))).thenReturn(stubResponse);
        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("findByName returns the role when it exists")
    void findByNameReturnsRole() {
        when(roleRepository.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(customRole));

        Role result = roleManagementService.findByName("CUSTOM_ROLE");

        assertThat(result).isSameAs(customRole);
    }

    @Test
    @DisplayName("findByName throws IllegalArgumentException with the name when not found")
    void findByNameNotFoundThrows() {
        when(roleRepository.findByName("MISSING_ROLE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleManagementService.findByName("MISSING_ROLE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not found: MISSING_ROLE");
    }

    @Test
    @DisplayName("getRoles maps findAll through the mapper into a PageResponse")
    void getRolesMapsFindAllThroughMapper() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(roleRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(customRole), pageable, 1));
        when(userService.getByEmail("superadmin@example.com")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);

        PageResponse<RoleResponse> result = roleManagementService.getRoles(pageable, "superadmin@example.com");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isSameAs(stubResponse);
        verify(roleRepository).findAll(pageable);
        verify(roleMapper).toResponse(customRole);
    }

    @Test
    @DisplayName("Tenant admin does not see the platform admin role in the role list")
    void tenantAdminDoesNotSeePlatformAdminRole() {
        Role platformAdminRole = Role.builder().id(4L).name(Roles.PLATFORM_ADMIN).build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(roleRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(customRole, platformAdminRole), pageable, 2));
        when(userService.getByEmail("tenantadmin@example.com")).thenReturn(tenantAdmin);
        when(authorizationService.isSuperAdmin(tenantAdmin)).thenReturn(false);

        PageResponse<RoleResponse> result = roleManagementService.getRoles(pageable, "tenantadmin@example.com");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isSameAs(stubResponse);
        verify(roleMapper).toResponse(customRole);
        verify(roleMapper, never()).toResponse(platformAdminRole);
    }

    @Test
    @DisplayName("Platform admin sees the platform admin role in the role list")
    void platformAdminSeesPlatformAdminRole() {
        Role platformAdminRole = Role.builder().id(4L).name(Roles.PLATFORM_ADMIN).build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(roleRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(customRole, platformAdminRole), pageable, 2));
        when(userService.getByEmail("superadmin@example.com")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);

        PageResponse<RoleResponse> result = roleManagementService.getRoles(pageable, "superadmin@example.com");

        assertThat(result.getContent()).hasSize(2);
        verify(roleMapper).toResponse(customRole);
        verify(roleMapper).toResponse(platformAdminRole);
    }

    @Test
    @DisplayName("getRoleById returns the mapped response for an existing role")
    void getRoleByIdReturnsMappedResponse() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));

        RoleResponse result = roleManagementService.getRoleById(1L);

        assertThat(result).isSameAs(stubResponse);
        verify(roleMapper).toResponse(customRole);
    }

    @Test
    @DisplayName("getRoleById throws IllegalArgumentException when the role does not exist")
    void getRoleByIdNotFoundThrows() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleManagementService.getRoleById(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not found");
    }

    @Test
    @DisplayName("Creating a role uppercases the name before saving and returns the mapped response")
    void createRoleUppercasesNameAndSaves() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("custom_role")
                .title("Custom")
                .description("Custom role")
                .build();
        when(roleRepository.existsByName("CUSTOM_ROLE")).thenReturn(false);

        RoleResponse result = roleManagementService.createRole(request);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        Role saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("CUSTOM_ROLE");
        assertThat(saved.getTitle()).isEqualTo("Custom");
        assertThat(saved.getDescription()).isEqualTo("Custom role");
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Creating a duplicate role throws IllegalArgumentException and never saves")
    void createDuplicateRoleThrows() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("CUSTOM_ROLE")
                .title("Custom")
                .build();
        when(roleRepository.existsByName("CUSTOM_ROLE")).thenReturn(true);

        assertThatThrownBy(() -> roleManagementService.createRole(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role already exists");
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("Creating a role whose name matches an existing role in different case is rejected")
    void createCaseInsensitiveDuplicateThrows() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("custom_role")
                .title("Custom")
                .build();
        when(roleRepository.existsByName("CUSTOM_ROLE")).thenReturn(true);

        assertThatThrownBy(() -> roleManagementService.createRole(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role already exists");
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("A save race condition converts DataIntegrityViolationException to Role already exists")
    void createRaceConditionConvertsDataIntegrityViolation() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("CUSTOM_ROLE")
                .title("Custom")
                .build();
        when(roleRepository.existsByName("CUSTOM_ROLE")).thenReturn(false);
        when(roleRepository.save(any(Role.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> roleManagementService.createRole(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role already exists");
    }

    @Test
    @DisplayName("Updating a custom role uppercases the name, sets title and description and saves")
    void updateCustomRoleSavesChanges() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("custom_role")
                .title("New Title")
                .description("New description")
                .build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(roleRepository.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(customRole));

        RoleResponse result = roleManagementService.updateRole(1L, request);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        Role saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("CUSTOM_ROLE");
        assertThat(saved.getTitle()).isEqualTo("New Title");
        assertThat(saved.getDescription()).isEqualTo("New description");
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Updating a nonexistent role throws IllegalArgumentException")
    void updateNonexistentRoleThrows() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("CUSTOM_ROLE")
                .build();
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleManagementService.updateRole(99L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not found");
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("Updating any of the 7 built-in roles throws IllegalArgumentException and never saves")
    void updateBuiltInRoleThrows() {
        List<String> builtInNames = List.of(
                Roles.PLATFORM_ADMIN, Roles.TENANT_ADMIN, Roles.USER_MANAGER,
                Roles.DEPARTMENT_MANAGER, Roles.EMPLOYEE, Roles.AUDITOR, Roles.FINANCE);
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("RENAMED")
                .build();

        for (int i = 0; i < builtInNames.size(); i++) {
            Role builtIn = Role.builder().id((long) (100 + i)).name(builtInNames.get(i)).build();
            when(roleRepository.findById(builtIn.getId())).thenReturn(Optional.of(builtIn));

            assertThatThrownBy(() -> roleManagementService.updateRole(builtIn.getId(), request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cannot update built-in role");
        }
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("Updating a role to a name owned by another role throws IllegalArgumentException")
    void updateToNameOwnedByAnotherRoleThrows() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("OTHER_ROLE")
                .build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(roleRepository.findByName("OTHER_ROLE")).thenReturn(Optional.of(otherRole));

        assertThatThrownBy(() -> roleManagementService.updateRole(1L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role name already in use");
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("Updating a role keeping its own name is allowed")
    void updateKeepingOwnNameIsAllowed() {
        RoleCreateRequest request = RoleCreateRequest.builder()
                .name("CUSTOM_ROLE")
                .title("Updated")
                .build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(roleRepository.findByName("CUSTOM_ROLE")).thenReturn(Optional.of(customRole));

        RoleResponse result = roleManagementService.updateRole(1L, request);

        verify(roleRepository).save(customRole);
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Deleting a custom role with no assigned users calls delete")
    void deleteCustomRoleCallsDelete() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(userService.countByRoleName("CUSTOM_ROLE")).thenReturn(0L);

        roleManagementService.deleteRole(1L);

        verify(roleRepository).delete(customRole);
    }

    @Test
    @DisplayName("Deleting a nonexistent role throws IllegalArgumentException")
    void deleteNonexistentRoleThrows() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleManagementService.deleteRole(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not found");
        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    @DisplayName("Deleting a built-in role throws IllegalArgumentException and never deletes")
    void deleteBuiltInRoleThrows() {
        when(roleRepository.findById(2L)).thenReturn(Optional.of(builtInRole));

        assertThatThrownBy(() -> roleManagementService.deleteRole(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot delete built-in role");
        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    @DisplayName("Deleting a role assigned to users throws IllegalArgumentException and never deletes")
    void deleteRoleAssignedToUsersThrows() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));
        when(userService.countByRoleName("CUSTOM_ROLE")).thenReturn(1L);

        assertThatThrownBy(() -> roleManagementService.deleteRole(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot delete role that is assigned to users");
        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    @DisplayName("Adding a permission to a custom role persists the permission and returns the mapped response")
    void addPermissionToCustomRoleSaves() {
        RolePermissionRequest request = RolePermissionRequest.builder()
                .permission(UserPermission.USER_READ)
                .build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));

        RoleResponse result = roleManagementService.addPermission(1L, request);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getPermissions()).contains(UserPermission.USER_READ);
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Removing a permission from a custom role persists the removal")
    void removePermissionFromCustomRoleSaves() {
        customRole.getPermissions().add(UserPermission.USER_READ);
        RolePermissionRequest request = RolePermissionRequest.builder()
                .permission(UserPermission.USER_READ)
                .build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));

        roleManagementService.removePermission(1L, request);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getPermissions()).doesNotContain(UserPermission.USER_READ);
    }

    @Test
    @DisplayName("Adding or removing a permission on a built-in role throws IllegalArgumentException and never saves")
    void modifyPermissionOnBuiltInRoleThrows() {
        RolePermissionRequest request = RolePermissionRequest.builder()
                .permission(UserPermission.USER_READ)
                .build();
        when(roleRepository.findById(2L)).thenReturn(Optional.of(builtInRole));

        assertThatThrownBy(() -> roleManagementService.addPermission(2L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot modify built-in role permissions");
        assertThatThrownBy(() -> roleManagementService.removePermission(2L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot modify built-in role permissions");
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("Adding or removing a permission on a nonexistent role throws IllegalArgumentException")
    void modifyPermissionOnNonexistentRoleThrows() {
        RolePermissionRequest request = RolePermissionRequest.builder()
                .permission(UserPermission.USER_READ)
                .build();
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleManagementService.addPermission(99L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not found");
        assertThatThrownBy(() -> roleManagementService.removePermission(99L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Role not found");
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    @DisplayName("Removing a permission the role does not have is an idempotent no-op that still saves")
    void removeMissingPermissionIsIdempotent() {
        RolePermissionRequest request = RolePermissionRequest.builder()
                .permission(UserPermission.USER_READ)
                .build();
        when(roleRepository.findById(1L)).thenReturn(Optional.of(customRole));

        RoleResponse result = roleManagementService.removePermission(1L, request);

        verify(roleRepository).save(customRole);
        assertThat(customRole.getPermissions()).isEmpty();
        assertThat(result).isSameAs(stubResponse);
    }
}
