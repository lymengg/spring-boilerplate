package com.example.demo.service;

import com.example.demo.constants.AuditActions;
import com.example.demo.dto.TenantCreateRequest;
import com.example.demo.dto.TenantUpdateRequest;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.TenantStatus;
import com.example.demo.entity.User;
import com.example.demo.mapper.TenantMapper;
import com.example.demo.repository.TenantRepository;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.impl.TenantManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantManagementServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private TenantManagementServiceImpl tenantManagementService;

    private User superAdmin;
    private User tenantAdmin;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        superAdmin = userWithUsername("superadmin", null);
        tenantAdmin = userWithUsername("tenantadmin", 1L);
        tenant = Tenant.builder().id(1L).name("Acme Corp").status(TenantStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("Super admin can get all tenants")
    void superAdminCanGetTenants() {
        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(tenant), pageable, 1));

        Page<?> result = tenantManagementService.getTenants(pageable, null, "superadmin");

        assertThat(result).hasSize(1);
        verify(tenantRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Non-super-admin cannot get tenants")
    void nonSuperAdminCannotGetTenants() {
        when(userService.getByUsername("tenantadmin")).thenReturn(tenantAdmin);
        when(authorizationService.isSuperAdmin(tenantAdmin)).thenReturn(false);

        assertThatThrownBy(() -> tenantManagementService.getTenants(PageRequest.of(0, 10), null, "tenantadmin"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only platform administrators can list tenants");
    }

    @Test
    @DisplayName("Super admin can search tenants by name")
    void superAdminCanSearchTenantsByName() {
        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantRepository.findByNameContainingIgnoreCase("acme", pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(tenant), pageable, 1));

        Page<?> result = tenantManagementService.getTenants(pageable, "acme", "superadmin");

        assertThat(result).hasSize(1);
        verify(tenantRepository).findByNameContainingIgnoreCase("acme", pageable);
        verify(tenantRepository, never()).findAll(any(PageRequest.class));
    }

    @Test
    @DisplayName("Search with blank name returns all tenants")
    void searchWithBlankNameReturnsAll() {
        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(authorizationService.isSuperAdmin(superAdmin)).thenReturn(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(Collections.singletonList(tenant), pageable, 1));

        Page<?> result = tenantManagementService.getTenants(pageable, "   ", "superadmin");

        assertThat(result).hasSize(1);
        verify(tenantRepository).findAll(pageable);
        verify(tenantRepository, never()).findByNameContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("Creating a tenant with duplicate name fails")
    void createTenantDuplicateNameFails() {
        TenantCreateRequest request = TenantCreateRequest.builder()
                .name("Acme Corp")
                .status(TenantStatus.ACTIVE)
                .build();

        when(tenantRepository.existsByName("Acme Corp")).thenReturn(true);

        assertThatThrownBy(() -> tenantManagementService.createTenant(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant already exists");
    }

    @Test
    @DisplayName("Creating a tenant saves and audits")
    void createTenantSavesAndAudits() {
        TenantCreateRequest request = TenantCreateRequest.builder()
                .name("New Tenant")
                .status(TenantStatus.ACTIVE)
                .build();

        when(tenantRepository.existsByName("New Tenant")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant t = invocation.getArgument(0);
            t.setId(2L);
            return t;
        });

        tenantManagementService.createTenant(request);

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Tenant");
        verify(auditLogService).record(AuditActions.TENANT_CREATED, AuditActions.RESOURCE_TENANT,
                "2", "Tenant created: New Tenant", null);
    }

    @Test
    @DisplayName("Updating a tenant with duplicate name fails")
    void updateTenantDuplicateNameFails() {
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .name("Other Tenant")
                .status(TenantStatus.ACTIVE)
                .build();

        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(authorizationService.canManageTenant(superAdmin, tenant)).thenReturn(true);

        Tenant other = Tenant.builder().id(3L).name("Other Tenant").status(TenantStatus.ACTIVE).build();
        when(tenantRepository.findByName("Other Tenant")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> tenantManagementService.updateTenant(1L, request, "superadmin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tenant name already in use");
    }

    @Test
    @DisplayName("Updating own tenant by name does not conflict")
    void updateTenantOwnNameDoesNotConflict() {
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .name("Acme Corp")
                .status(TenantStatus.INACTIVE)
                .build();

        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(authorizationService.canManageTenant(superAdmin, tenant)).thenReturn(true);
        when(tenantRepository.findByName("Acme Corp")).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tenantManagementService.updateTenant(1L, request, "superadmin");

        verify(auditLogService).record(AuditActions.TENANT_UPDATED, AuditActions.RESOURCE_TENANT,
                "1", "Tenant updated: Acme Corp", "superadmin");
    }

    @Test
    @DisplayName("Deleting a tenant audits the action")
    void deleteTenantAuditsAction() {
        when(userService.getByUsername("superadmin")).thenReturn(superAdmin);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(authorizationService.canManageTenant(superAdmin, tenant)).thenReturn(true);

        tenantManagementService.deleteTenant(1L, "superadmin");

        verify(tenantRepository).delete(tenant);
        verify(auditLogService).record(AuditActions.TENANT_DELETED, AuditActions.RESOURCE_TENANT,
                "1", "Tenant deleted: Acme Corp", "superadmin");
    }

    @Test
    @DisplayName("Non-super-admin cannot update a tenant they don't manage")
    void nonSuperAdminCannotUpdateUnmanagedTenant() {
        TenantUpdateRequest request = TenantUpdateRequest.builder()
                .name("Acme Corp")
                .status(TenantStatus.ACTIVE)
                .build();

        when(userService.getByUsername("tenantadmin")).thenReturn(tenantAdmin);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(authorizationService.canManageTenant(tenantAdmin, tenant)).thenReturn(false);

        assertThatThrownBy(() -> tenantManagementService.updateTenant(1L, request, "tenantadmin"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cannot update this tenant");
    }

    @Test
    @DisplayName("Non-super-admin cannot delete a tenant they don't manage")
    void nonSuperAdminCannotDeleteUnmanagedTenant() {
        when(userService.getByUsername("tenantadmin")).thenReturn(tenantAdmin);
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(authorizationService.canManageTenant(tenantAdmin, tenant)).thenReturn(false);

        assertThatThrownBy(() -> tenantManagementService.deleteTenant(1L, "tenantadmin"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cannot delete this tenant");
    }

    private User userWithUsername(String username, Long tenantId) {
        User user = User.builder()
                .id((long) username.hashCode())
                .username(username)
                .password("secret")
                .enabled(true)
                .accountNonLocked(true)
                .build();
        user.setRoles(new HashSet<>());
        if (tenantId == null) {
            user.getRoles().add(com.example.demo.entity.Role.builder()
                    .name("PLATFORM_ADMIN").permissions(new HashSet<>()).build());
        } else {
            user.setTenant(Tenant.builder().id(tenantId).build());
            user.getRoles().add(com.example.demo.entity.Role.builder()
                    .name("TENANT_ADMIN").permissions(new HashSet<>()).build());
        }
        return user;
    }
}
