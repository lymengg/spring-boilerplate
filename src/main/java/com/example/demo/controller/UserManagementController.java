package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserManagementResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import com.example.demo.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/management/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserManagementResponse>>> getUsers() {
        List<UserManagementResponse> users = userManagementService.getUsers();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserManagementResponse>> getUserById(@PathVariable Long id) {
        UserManagementResponse user = userManagementService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserManagementResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication) {
        UserManagementResponse user = userManagementService.updateUser(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {
        userManagementService.deleteUser(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<UserManagementResponse>> toggleUserEnabled(
            @PathVariable Long id,
            @Valid @RequestBody UserEnableRequest request,
            Authentication authentication) {
        UserManagementResponse user = userManagementService.toggleUserEnabled(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User enabled state updated", user));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<UserManagementResponse>> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleAssignmentRequest request,
            Authentication authentication) {
        UserManagementResponse user = userManagementService.assignRole(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Role assigned successfully", user));
    }

    @DeleteMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<UserManagementResponse>> removeRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleAssignmentRequest request,
            Authentication authentication) {
        UserManagementResponse user = userManagementService.removeRole(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Role removed successfully", user));
    }
}
