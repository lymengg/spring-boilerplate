package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.RoleCreateRequest;
import com.example.demo.dto.RolePermissionRequest;
import com.example.demo.dto.RoleResponse;
import com.example.demo.service.RoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/management/roles")
@RequiredArgsConstructor
public class RoleManagementController {

    private final RoleManagementService roleManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getRoles() {
        List<RoleResponse> roles = roleManagementService.getRoles();
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved successfully", roles));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        RoleResponse role = roleManagementService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.success("Role retrieved successfully", role));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleCreateRequest request) {
        RoleResponse role = roleManagementService.createRole(request);
        return ResponseEntity.ok(ApiResponse.success("Role created successfully", role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleCreateRequest request) {
        RoleResponse role = roleManagementService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", role));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable Long id) {
        roleManagementService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Role deleted successfully", null));
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> addPermission(
            @PathVariable Long id,
            @Valid @RequestBody RolePermissionRequest request) {
        RoleResponse role = roleManagementService.addPermission(id, request);
        return ResponseEntity.ok(ApiResponse.success("Permission added successfully", role));
    }

    @DeleteMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> removePermission(
            @PathVariable Long id,
            @Valid @RequestBody RolePermissionRequest request) {
        RoleResponse role = roleManagementService.removePermission(id, request);
        return ResponseEntity.ok(ApiResponse.success("Permission removed successfully", role));
    }
}
