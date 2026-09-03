package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.TenantCreateRequest;
import com.example.demo.dto.TenantResponse;
import com.example.demo.dto.TenantUpdateRequest;
import com.example.demo.service.TenantManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/tenants")
@RequiredArgsConstructor
public class TenantManagementController {

    private final TenantManagementService tenantManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TenantResponse>>> getTenants(
            @PageableDefault Pageable pageable,
            @RequestParam(required = false) String name,
            Authentication authentication) {
        Page<TenantResponse> tenants = tenantManagementService.getTenants(pageable, name, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Tenants retrieved successfully", tenants));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenantById(
            @PathVariable Long id,
            Authentication authentication) {
        TenantResponse tenant = tenantManagementService.getTenantById(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Tenant retrieved successfully", tenant));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @Valid @RequestBody TenantCreateRequest request,
            Authentication authentication) {
        TenantResponse tenant = tenantManagementService.createTenant(request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Tenant created successfully", tenant));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TenantResponse>> updateTenant(
            @PathVariable Long id,
            @Valid @RequestBody TenantUpdateRequest request,
            Authentication authentication) {
        TenantResponse tenant = tenantManagementService.updateTenant(id, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Tenant updated successfully", tenant));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTenant(
            @PathVariable Long id,
            Authentication authentication) {
        tenantManagementService.deleteTenant(id, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Tenant deleted successfully", null));
    }
}
