package com.example.demo.service;

import com.example.demo.dto.RoleCreateRequest;
import com.example.demo.dto.RolePermissionRequest;
import com.example.demo.dto.RoleResponse;
import com.example.demo.constants.Roles;
import com.example.demo.constants.UserPermission;
import com.example.demo.entity.Role;
import com.example.demo.mapper.RoleMapper;
import com.example.demo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final UserService userService;
    private final RoleMapper roleMapper;

    @Transactional(readOnly = true)
    public Role findByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public Page<RoleResponse> getRoles(Pageable pageable) {
        return roleRepository.findAll(pageable).map(roleMapper::toResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public RoleResponse getRoleById(Long id) {
        return roleMapper.toResponse(roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found")));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    public RoleResponse createRole(RoleCreateRequest request) {
        String roleName = request.getName().toUpperCase();
        if (roleRepository.existsByName(roleName)) {
            throw new IllegalArgumentException("Role already exists");
        }

        Role role = Role.builder()
                .name(roleName)
                .title(request.getTitle())
                .description(request.getDescription())
                .build();

        try {
            return roleMapper.toResponse(roleRepository.save(role));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("Role already exists");
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    public RoleResponse updateRole(Long id, RoleCreateRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (isBuiltIn(role)) {
            throw new IllegalArgumentException("Cannot update built-in role");
        }

        String newName = request.getName().toUpperCase();
        roleRepository.findByName(newName).ifPresent(other -> {
            if (!other.getId().equals(role.getId())) {
                throw new IllegalArgumentException("Role name already in use");
            }
        });

        role.setName(newName);
        role.setTitle(request.getTitle());
        role.setDescription(request.getDescription());

        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (isBuiltIn(role)) {
            throw new IllegalArgumentException("Cannot delete built-in role");
        }

        if (userService.countByRoleName(role.getName()) > 0) {
            throw new IllegalArgumentException("Cannot delete role that is assigned to users");
        }

        roleRepository.delete(role);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_PERMISSION')")
    public RoleResponse addPermission(Long id, RolePermissionRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (isBuiltIn(role)) {
            throw new IllegalArgumentException("Cannot modify built-in role permissions");
        }

        role.getPermissions().add(request.getPermission());
        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_ASSIGN_PERMISSION')")
    public RoleResponse removePermission(Long id, RolePermissionRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));

        if (isBuiltIn(role)) {
            throw new IllegalArgumentException("Cannot modify built-in role permissions");
        }

        role.getPermissions().remove(request.getPermission());
        return roleMapper.toResponse(roleRepository.save(role));
    }

    private boolean isBuiltIn(Role role) {
        return Set.of(
                Roles.PLATFORM_ADMIN, Roles.TENANT_ADMIN, Roles.USER_MANAGER,
                Roles.DEPARTMENT_MANAGER, Roles.EMPLOYEE, Roles.AUDITOR,
                Roles.FINANCE, Roles.USER
        ).contains(role.getName());
    }
}
