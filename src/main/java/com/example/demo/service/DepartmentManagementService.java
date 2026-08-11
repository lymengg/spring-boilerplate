package com.example.demo.service;

import com.example.demo.dto.DepartmentCreateRequest;
import com.example.demo.dto.DepartmentResponse;
import com.example.demo.dto.DepartmentUpdateRequest;
import com.example.demo.entity.Department;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.DepartmentMapper;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.security.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentManagementService {

    private final DepartmentRepository departmentRepository;
    private final TenantManagementService tenantManagementService;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final DepartmentMapper departmentMapper;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    public Page<DepartmentResponse> getDepartments(Pageable pageable, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        if (authorizationService.isSuperAdmin(currentUser)) {
            return departmentRepository.findAll(pageable).map(departmentMapper::toResponse);
        }
        if (currentUser.getTenant() == null) {
            return Page.empty(pageable);
        }
        return departmentRepository.findAllByTenantId(currentUser.getTenant().getId(), pageable)
                .map(departmentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DEPARTMENT_READ')")
    public DepartmentResponse getDepartmentById(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Department department = findAccessibleDepartment(id, currentUser);
        return departmentMapper.toResponse(department);
    }

    @Transactional
    @PreAuthorize("hasAuthority('DEPARTMENT_CREATE')")
    public DepartmentResponse createDepartment(DepartmentCreateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Tenant tenant = tenantManagementService.findById(request.getTenantId());
        if (!authorizationService.canManageTenant(currentUser, tenant)) {
            throw new AccessDeniedException("Cannot create department in this tenant");
        }
        if (departmentRepository.existsByNameAndTenantId(request.getName(), tenant.getId())) {
            throw new IllegalArgumentException("Department already exists in this tenant");
        }
        User manager = resolveManager(request.getManagerId(), tenant);
        Department department = Department.builder()
                .name(request.getName())
                .tenant(tenant)
                .manager(manager)
                .build();
        return departmentMapper.toResponse(departmentRepository.save(department));
    }

    @Transactional
    @PreAuthorize("hasAuthority('DEPARTMENT_UPDATE')")
    public DepartmentResponse updateDepartment(Long id, DepartmentUpdateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Department department = findManageableDepartment(id, currentUser);
        departmentRepository.findByNameAndTenantId(request.getName(), department.getTenant().getId())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Department name already in use");
                });
        department.setName(request.getName());
        department.setManager(resolveManager(request.getManagerId(), department.getTenant()));
        return departmentMapper.toResponse(departmentRepository.save(department));
    }

    @Transactional
    @PreAuthorize("hasAuthority('DEPARTMENT_DELETE')")
    public void deleteDepartment(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Department department = findManageableDepartment(id, currentUser);
        departmentRepository.delete(department);
    }

    @Transactional(readOnly = true)
    public Department findById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
    }

    private Department findAccessibleDepartment(Long id, User user) {
        if (authorizationService.isSuperAdmin(user)) {
            return departmentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        }
        if (user.getTenant() != null) {
            return departmentRepository.findByIdAndTenantId(id, user.getTenant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        }
        throw new AccessDeniedException("Cannot access this department");
    }

    private Department findManageableDepartment(Long id, User user) {
        Department department = findAccessibleDepartment(id, user);
        if (!authorizationService.managesDepartment(user, department)) {
            throw new AccessDeniedException("Cannot manage this department");
        }
        return department;
    }

    private User resolveManager(Long managerId, Tenant tenant) {
        if (managerId == null) {
            return null;
        }
        User manager = userService.getById(managerId);
        if (manager.getTenant() == null || !manager.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException("Manager must belong to the same tenant");
        }
        return manager;
    }
}
