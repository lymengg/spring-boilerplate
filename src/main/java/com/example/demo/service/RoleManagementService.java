package com.example.demo.service;

import com.example.demo.dto.RoleCreateRequest;
import com.example.demo.dto.RolePermissionRequest;
import com.example.demo.dto.RoleResponse;
import com.example.demo.entity.Role;
import com.example.demo.entity.UserPermission;
import com.example.demo.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public List<RoleResponse> getRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ROLE_READ')")
    public RoleResponse getRoleById(Long id) {
        return mapToResponse(roleRepository.findById(id)
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
                .description(request.getDescription())
                .build();

        try {
            return mapToResponse(roleRepository.save(role));
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
        role.setDescription(request.getDescription());

        return mapToResponse(roleRepository.save(role));
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
        return mapToResponse(roleRepository.save(role));
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
        return mapToResponse(roleRepository.save(role));
    }

    private boolean isBuiltIn(Role role) {
        return "ADMIN".equals(role.getName()) || "USER".equals(role.getName()) || "USER_MANAGER".equals(role.getName());
    }

    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(Set.copyOf(role.getPermissions()))
                .build();
    }
}
