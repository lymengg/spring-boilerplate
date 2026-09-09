package com.example.demo.service;

import com.example.demo.dto.PageResponse;
import com.example.demo.dto.TenantCreateRequest;
import com.example.demo.dto.TenantResponse;
import com.example.demo.dto.TenantUpdateRequest;
import com.example.demo.entity.Tenant;
import org.springframework.data.domain.Pageable;

public interface TenantManagementService {

    PageResponse<TenantResponse> getTenants(Pageable pageable, String name, String currentEmail);

    TenantResponse getTenantById(Long id, String currentEmail);

    Tenant findById(Long id);

    TenantResponse createTenant(TenantCreateRequest request, String currentEmail);

    TenantResponse updateTenant(Long id, TenantUpdateRequest request, String currentEmail);

    void deleteTenant(Long id, String currentEmail);
}
