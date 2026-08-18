package com.example.demo.service;

import com.example.demo.dto.RoleCreateRequest;
import com.example.demo.dto.RolePermissionRequest;
import com.example.demo.dto.RoleResponse;
import com.example.demo.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoleManagementService {

    Role findByName(String name);

    Page<RoleResponse> getRoles(Pageable pageable);

    RoleResponse getRoleById(Long id);

    RoleResponse createRole(RoleCreateRequest request);

    RoleResponse updateRole(Long id, RoleCreateRequest request);

    void deleteRole(Long id);

    RoleResponse addPermission(Long id, RolePermissionRequest request);

    RoleResponse removePermission(Long id, RolePermissionRequest request);
}
