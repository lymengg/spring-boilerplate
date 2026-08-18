package com.example.demo.service.impl;

import com.example.demo.dto.TenantCreateRequest;
import com.example.demo.dto.TenantResponse;
import com.example.demo.dto.TenantUpdateRequest;
import com.example.demo.entity.Tenant;
import com.example.demo.entity.User;
import com.example.demo.mapper.TenantMapper;
import com.example.demo.repository.TenantRepository;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.TenantManagementService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantManagementServiceImpl implements TenantManagementService {

    private final TenantRepository tenantRepository;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final TenantMapper tenantMapper;

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public Page<TenantResponse> getTenants(Pageable pageable, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        if (authorizationService.isSuperAdmin(currentUser)) {
            return tenantRepository.findAll(pageable).map(tenantMapper::toResponse);
        }
        if (currentUser.getTenant() == null) {
            return Page.empty(pageable);
        }
        return tenantRepository.findById(currentUser.getTenant().getId())
                .map(tenant -> (Page<TenantResponse>) new PageImpl<>(List.of(tenantMapper.toResponse(tenant)), pageable, 1))
                .orElseGet(() -> Page.empty(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public TenantResponse getTenantById(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        if (!authorizationService.canAccessTenant(currentUser, tenant)) {
            throw new AccessDeniedException("Cannot access this tenant");
        }
        return tenantMapper.toResponse(tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public Tenant findById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TENANT_CREATE')")
    public TenantResponse createTenant(TenantCreateRequest request) {
        if (tenantRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Tenant already exists");
        }
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .status(request.getStatus())
                .build();
        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TENANT_UPDATE')")
    public TenantResponse updateTenant(Long id, TenantUpdateRequest request, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        if (!authorizationService.canManageTenant(currentUser, tenant)) {
            throw new AccessDeniedException("Cannot update this tenant");
        }
        tenantRepository.findByName(request.getName()).ifPresent(other -> {
            if (!other.getId().equals(tenant.getId())) {
                throw new IllegalArgumentException("Tenant name already in use");
            }
        });
        tenant.setName(request.getName());
        tenant.setStatus(request.getStatus());
        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('TENANT_DELETE')")
    public void deleteTenant(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        if (!authorizationService.canManageTenant(currentUser, tenant)) {
            throw new AccessDeniedException("Cannot delete this tenant");
        }
        tenantRepository.delete(tenant);
    }
}
