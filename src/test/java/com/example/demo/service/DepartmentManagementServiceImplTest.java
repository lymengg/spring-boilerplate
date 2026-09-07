package com.example.demo.service;

import com.example.demo.dto.DepartmentCreateRequest;
import com.example.demo.dto.DepartmentResponse;
import com.example.demo.dto.DepartmentUpdateRequest;
import com.example.demo.dto.PageResponse;
import com.example.demo.entity.Department;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.DepartmentMapper;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.impl.DepartmentManagementServiceImpl;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepartmentManagementServiceImplTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private TenantManagementService tenantManagementService;

    @Mock
    private UserService userService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private DepartmentManagementServiceImpl departmentManagementService;

    private Tenant tenant;
    private Tenant otherTenant;
    private Department dept;
    private User admin;
    private User crossTenantUser;
    private User noTenantUser;
    private User manager;
    private DepartmentResponse stubResponse;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().id(1L).name("Tenant 1").build();
        otherTenant = Tenant.builder().id(2L).name("Tenant 2").build();
        dept = Department.builder().id(10L).name("HR").tenant(tenant).build();
        admin = userWithTenant("admin", tenant);
        crossTenantUser = userWithTenant("crossTenantUser", otherTenant);
        noTenantUser = userWithTenant("noTenantUser", null);
        manager = userWithTenant("manager", tenant);
        stubResponse = DepartmentResponse.builder().id(10L).name("HR").build();

        when(userService.getByUsername("admin")).thenReturn(admin);
        when(userService.getByUsername("crossTenantUser")).thenReturn(crossTenantUser);
        when(userService.getByUsername("noTenantUser")).thenReturn(noTenantUser);
        when(userService.getByUsername("manager")).thenReturn(manager);
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(departmentMapper.toResponse(any(Department.class))).thenReturn(stubResponse);
    }

    @Test
    @DisplayName("Super admin listing departments uses findAll")
    void getDepartmentsAsSuperAdminUsesFindAll() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(authorizationService.isSuperAdmin(admin)).thenReturn(true);
        when(departmentRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(dept), pageable, 1));

        PageResponse<DepartmentResponse> result = departmentManagementService.getDepartments(pageable, "admin");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0)).isSameAs(stubResponse);
        verify(departmentRepository).findAll(pageable);
        verify(departmentRepository, never()).findAllByTenantId(any(), any());
    }

    @Test
    @DisplayName("Tenant user listing departments uses findAllByTenantId")
    void getDepartmentsAsTenantUserUsesFindAllByTenantId() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findAllByTenantId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(dept), pageable, 1));

        PageResponse<DepartmentResponse> result = departmentManagementService.getDepartments(pageable, "admin");

        assertThat(result.getContent()).hasSize(1);
        verify(departmentRepository).findAllByTenantId(1L, pageable);
        verify(departmentRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("User with no tenant gets an empty page and the repository is never queried")
    void getDepartmentsForUserWithoutTenantReturnsEmpty() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(authorizationService.isSuperAdmin(noTenantUser)).thenReturn(false);

        PageResponse<DepartmentResponse> result = departmentManagementService.getDepartments(pageable, "noTenantUser");

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getContent()).isEmpty();
        verify(departmentRepository, never()).findAll(any(Pageable.class));
        verify(departmentRepository, never()).findAllByTenantId(any(), any());
    }

    @Test
    @DisplayName("getDepartmentById returns the mapped response for an accessible department")
    void getDepartmentByIdReturnsMappedResponse() {
        when(authorizationService.isSuperAdmin(admin)).thenReturn(true);
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));

        DepartmentResponse result = departmentManagementService.getDepartmentById(10L, "admin");

        assertThat(result).isSameAs(stubResponse);
        verify(departmentMapper).toResponse(dept);
    }

    @Test
    @DisplayName("getDepartmentById throws IllegalArgumentException when the department does not exist")
    void getDepartmentByIdNotFoundThrows() {
        when(authorizationService.isSuperAdmin(admin)).thenReturn(true);
        when(departmentRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentManagementService.getDepartmentById(10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Department not found");
    }

    @Test
    @DisplayName("Cross-tenant getDepartmentById is treated as not found with no tenant leak")
    void getDepartmentByIdCrossTenantThrowsNotFound() {
        when(authorizationService.isSuperAdmin(crossTenantUser)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentManagementService.getDepartmentById(10L, "crossTenantUser"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Department not found");
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getDepartmentById for a user with no tenant throws AccessDeniedException")
    void getDepartmentByIdForUserWithoutTenantDenied() {
        when(authorizationService.isSuperAdmin(noTenantUser)).thenReturn(false);

        assertThatThrownBy(() -> departmentManagementService.getDepartmentById(10L, "noTenantUser"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot access this department");
        verify(departmentRepository, never()).findById(any());
        verify(departmentRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    @DisplayName("findById returns the department when it exists")
    void findByIdReturnsDepartment() {
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));

        Department result = departmentManagementService.findById(10L);

        assertThat(result).isSameAs(dept);
    }

    @Test
    @DisplayName("findById throws IllegalArgumentException when the department does not exist")
    void findByIdNotFoundThrows() {
        when(departmentRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentManagementService.findById(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Department not found");
    }

    @Test
    @DisplayName("findByManagersId delegates to the repository")
    void findByManagersIdDelegatesToRepository() {
        when(departmentRepository.findByManagersId(1L)).thenReturn(List.of(dept));

        List<Department> result = departmentManagementService.findByManagersId(1L);

        assertThat(result).containsExactly(dept);
        verify(departmentRepository).findByManagersId(1L);
    }

    @Test
    @DisplayName("Creating a department resolves the tenant, validates management and saves name, tenant and managers")
    void createDepartmentSavesWithNameTenantAndManagers() {
        DepartmentCreateRequest request = DepartmentCreateRequest.builder()
                .name("Engineering")
                .tenantId(1L)
                .managerIds(List.of(manager.getId()))
                .build();
        when(tenantManagementService.findById(1L)).thenReturn(tenant);
        when(authorizationService.canManageTenant(admin, tenant)).thenReturn(true);
        when(departmentRepository.existsByNameAndTenantId("Engineering", 1L)).thenReturn(false);
        when(userService.getById(manager.getId())).thenReturn(manager);

        DepartmentResponse result = departmentManagementService.createDepartment(request, "admin");

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        Department saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Engineering");
        assertThat(saved.getTenant()).isSameAs(tenant);
        assertThat(saved.getManagers()).containsExactly(manager);
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Creating a department in a tenant the user cannot manage throws AccessDeniedException and never saves")
    void createDepartmentInUnmanageableTenantDenied() {
        DepartmentCreateRequest request = DepartmentCreateRequest.builder()
                .name("Engineering")
                .tenantId(1L)
                .build();
        when(tenantManagementService.findById(1L)).thenReturn(tenant);
        when(authorizationService.canManageTenant(admin, tenant)).thenReturn(false);

        assertThatThrownBy(() -> departmentManagementService.createDepartment(request, "admin"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot create department in this tenant");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Creating a department with a nonexistent tenant propagates IllegalArgumentException")
    void createDepartmentWithNonexistentTenantPropagates() {
        DepartmentCreateRequest request = DepartmentCreateRequest.builder()
                .name("Engineering")
                .tenantId(99L)
                .build();
        when(tenantManagementService.findById(99L))
                .thenThrow(new IllegalArgumentException("Tenant not found"));

        assertThatThrownBy(() -> departmentManagementService.createDepartment(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tenant not found");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Creating a department with a duplicate name in the same tenant throws IllegalArgumentException")
    void createDepartmentDuplicateNameThrows() {
        DepartmentCreateRequest request = DepartmentCreateRequest.builder()
                .name("HR")
                .tenantId(1L)
                .build();
        when(tenantManagementService.findById(1L)).thenReturn(tenant);
        when(authorizationService.canManageTenant(admin, tenant)).thenReturn(true);
        when(departmentRepository.existsByNameAndTenantId("HR", 1L)).thenReturn(true);

        assertThatThrownBy(() -> departmentManagementService.createDepartment(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Department already exists in this tenant");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Creating a department with an empty or null manager list saves with an empty manager set")
    void createDepartmentWithNoManagersSavesEmptySet() {
        DepartmentCreateRequest request = DepartmentCreateRequest.builder()
                .name("Sales")
                .tenantId(1L)
                .build();
        when(tenantManagementService.findById(1L)).thenReturn(tenant);
        when(authorizationService.canManageTenant(admin, tenant)).thenReturn(true);
        when(departmentRepository.existsByNameAndTenantId("Sales", 1L)).thenReturn(false);

        departmentManagementService.createDepartment(request, "admin");

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        assertThat(captor.getValue().getManagers()).isEmpty();
    }

    @Test
    @DisplayName("Creating a department with a cross-tenant manager throws IllegalArgumentException and never saves")
    void createDepartmentWithCrossTenantManagerThrows() {
        DepartmentCreateRequest request = DepartmentCreateRequest.builder()
                .name("Engineering")
                .tenantId(1L)
                .managerIds(List.of(crossTenantUser.getId()))
                .build();
        when(tenantManagementService.findById(1L)).thenReturn(tenant);
        when(authorizationService.canManageTenant(admin, tenant)).thenReturn(true);
        when(departmentRepository.existsByNameAndTenantId("Engineering", 1L)).thenReturn(false);
        when(userService.getById(crossTenantUser.getId())).thenReturn(crossTenantUser);

        assertThatThrownBy(() -> departmentManagementService.createDepartment(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Manager must belong to the same tenant");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Creating a department with a nonexistent manager id propagates IllegalArgumentException")
    void createDepartmentWithNonexistentManagerPropagates() {
        DepartmentCreateRequest request = DepartmentCreateRequest.builder()
                .name("Engineering")
                .tenantId(1L)
                .managerIds(List.of(99L))
                .build();
        when(tenantManagementService.findById(1L)).thenReturn(tenant);
        when(authorizationService.canManageTenant(admin, tenant)).thenReturn(true);
        when(departmentRepository.existsByNameAndTenantId("Engineering", 1L)).thenReturn(false);
        when(userService.getById(99L)).thenThrow(new IllegalArgumentException("User not found"));

        assertThatThrownBy(() -> departmentManagementService.createDepartment(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Updating a department updates the name and managers and saves")
    void updateDepartmentUpdatesNameAndManagers() {
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name("PeopleOps")
                .managerIds(List.of(manager.getId()))
                .build();
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));
        when(authorizationService.managesDepartment(admin, dept)).thenReturn(true);
        when(departmentRepository.findByNameAndTenantId("PeopleOps", 1L)).thenReturn(Optional.empty());
        when(userService.getById(manager.getId())).thenReturn(manager);

        DepartmentResponse result = departmentManagementService.updateDepartment(10L, request, "admin");

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        Department saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("PeopleOps");
        assertThat(saved.getManagers()).containsExactly(manager);
        assertThat(result).isSameAs(stubResponse);
    }

    @Test
    @DisplayName("Updating a department to a name used by another department in the same tenant throws IllegalArgumentException")
    void updateDepartmentToDuplicateNameThrows() {
        Department other = Department.builder().id(20L).name("Sales").tenant(tenant).build();
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name("Sales")
                .build();
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));
        when(authorizationService.managesDepartment(admin, dept)).thenReturn(true);
        when(departmentRepository.findByNameAndTenantId("Sales", 1L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> departmentManagementService.updateDepartment(10L, request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Department name already in use");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Updating a department keeping its own name is allowed")
    void updateDepartmentKeepingOwnNameIsAllowed() {
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name("HR")
                .build();
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));
        when(authorizationService.managesDepartment(admin, dept)).thenReturn(true);
        when(departmentRepository.findByNameAndTenantId("HR", 1L)).thenReturn(Optional.of(dept));

        departmentManagementService.updateDepartment(10L, request, "admin");

        verify(departmentRepository).save(dept);
    }

    @Test
    @DisplayName("Updating a department the user cannot manage throws AccessDeniedException and never saves")
    void updateDepartmentNotManageableDenied() {
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name("PeopleOps")
                .build();
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));
        when(authorizationService.managesDepartment(admin, dept)).thenReturn(false);

        assertThatThrownBy(() -> departmentManagementService.updateDepartment(10L, request, "admin"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot manage this department");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Updating a nonexistent department throws IllegalArgumentException")
    void updateNonexistentDepartmentThrows() {
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name("PeopleOps")
                .build();
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentManagementService.updateDepartment(10L, request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Department not found");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Updating a department with a cross-tenant manager throws IllegalArgumentException and never saves")
    void updateDepartmentWithCrossTenantManagerThrows() {
        DepartmentUpdateRequest request = DepartmentUpdateRequest.builder()
                .name("PeopleOps")
                .managerIds(List.of(crossTenantUser.getId()))
                .build();
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));
        when(authorizationService.managesDepartment(admin, dept)).thenReturn(true);
        when(departmentRepository.findByNameAndTenantId("PeopleOps", 1L)).thenReturn(Optional.empty());
        when(userService.getById(crossTenantUser.getId())).thenReturn(crossTenantUser);

        assertThatThrownBy(() -> departmentManagementService.updateDepartment(10L, request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Manager must belong to the same tenant");
        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    @DisplayName("Deleting a manageable department calls delete")
    void deleteManageableDepartmentCallsDelete() {
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));
        when(authorizationService.managesDepartment(admin, dept)).thenReturn(true);

        departmentManagementService.deleteDepartment(10L, "admin");

        verify(departmentRepository).delete(dept);
    }

    @Test
    @DisplayName("Deleting a nonexistent department throws IllegalArgumentException")
    void deleteNonexistentDepartmentThrows() {
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentManagementService.deleteDepartment(10L, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Department not found");
        verify(departmentRepository, never()).delete(any(Department.class));
    }

    @Test
    @DisplayName("Deleting a department the user cannot manage throws AccessDeniedException and never deletes")
    void deleteDepartmentNotManageableDenied() {
        when(authorizationService.isSuperAdmin(admin)).thenReturn(false);
        when(departmentRepository.findByIdAndTenantId(10L, 1L)).thenReturn(Optional.of(dept));
        when(authorizationService.managesDepartment(admin, dept)).thenReturn(false);

        assertThatThrownBy(() -> departmentManagementService.deleteDepartment(10L, "admin"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Cannot manage this department");
        verify(departmentRepository, never()).delete(any(Department.class));
    }

    private User userWithTenant(String username, Tenant userTenant) {
        return User.builder()
                .id((long) username.hashCode())
                .username(username)
                .password("secret")
                .enabled(true)
                .accountNonLocked(true)
                .tenant(userTenant)
                .build();
    }
}
