package com.example.demo.service;

import com.example.demo.dto.TenantCreateRequest;
import com.example.demo.dto.TenantResponse;
import com.example.demo.dto.TenantUpdateRequest;
import com.example.demo.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TenantManagementService {

    Page<TenantResponse> getTenants(Pageable pageable, String name, String currentUsername);

    TenantResponse getTenantById(Long id, String currentUsername);

    Tenant findById(Long id);

    TenantResponse createTenant(TenantCreateRequest request, String currentUsername);

    TenantResponse updateTenant(Long id, TenantUpdateRequest request, String currentUsername);

    void deleteTenant(Long id, String currentUsername);
}
