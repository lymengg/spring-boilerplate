package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.UserCreateRequest;
import com.example.demo.dto.UserEnableRequest;
import com.example.demo.dto.UserResponse;
import com.example.demo.dto.UserRoleAssignmentRequest;
import com.example.demo.dto.UserUpdateRequest;
import com.example.demo.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request,
            Authentication authentication) {
        UserResponse user = userManagementService.createUser(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User created successfully", user));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(Pageable pageable, Authentication authentication) {
        Page<UserResponse> users = userManagementService.getUsers(pageable, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id, Authentication authentication) {
        UserResponse user = userManagementService.getUserById(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User retrieved successfully", user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication) {
        UserResponse user = userManagementService.updateUser(id, request, authentication.getName());
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
    public ResponseEntity<ApiResponse<UserResponse>> toggleUserEnabled(
            @PathVariable Long id,
            @Valid @RequestBody UserEnableRequest request,
            Authentication authentication) {
        UserResponse user = userManagementService.toggleUserEnabled(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("User enabled state updated", user));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<UserResponse>> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleAssignmentRequest request,
            Authentication authentication) {
        UserResponse user = userManagementService.assignRole(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Role assigned successfully", user));
    }

    @DeleteMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<UserResponse>> removeRole(
            @PathVariable Long id,
            @Valid @RequestBody UserRoleAssignmentRequest request,
            Authentication authentication) {
        UserResponse user = userManagementService.removeRole(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Role removed successfully", user));
    }
}
