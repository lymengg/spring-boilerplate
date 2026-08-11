package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.DepartmentCreateRequest;
import com.example.demo.dto.DepartmentResponse;
import com.example.demo.dto.DepartmentUpdateRequest;
import com.example.demo.service.DepartmentManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/departments")
@RequiredArgsConstructor
public class DepartmentManagementController {

    private final DepartmentManagementService departmentManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DepartmentResponse>>> getDepartments(
            @PageableDefault Pageable pageable,
            Authentication authentication) {
        Page<DepartmentResponse> departments = departmentManagementService.getDepartments(pageable, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Departments retrieved successfully", departments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartmentById(
            @PathVariable Long id,
            Authentication authentication) {
        DepartmentResponse department = departmentManagementService.getDepartmentById(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Department retrieved successfully", department));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
            @Valid @RequestBody DepartmentCreateRequest request,
            Authentication authentication) {
        DepartmentResponse department = departmentManagementService.createDepartment(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Department created successfully", department));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @PathVariable Long id,
            @Valid @RequestBody DepartmentUpdateRequest request,
            Authentication authentication) {
        DepartmentResponse department = departmentManagementService.updateDepartment(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully", department));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDepartment(
            @PathVariable Long id,
            Authentication authentication) {
        departmentManagementService.deleteDepartment(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Department deleted successfully", null));
    }
}
