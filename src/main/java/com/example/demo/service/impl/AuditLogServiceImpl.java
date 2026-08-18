package com.example.demo.service.impl;

import com.example.demo.dto.AuditLogResponse;
import com.example.demo.entity.AuditLog;
import com.example.demo.entity.User;
import com.example.demo.mapper.AuditLogMapper;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.AuditLogService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final AuditLogMapper auditLogMapper;

    @Override
    @Transactional
    public void record(String action, String resourceType, String resourceId, String details, String actorUsername) {
        User actor = userService.getByUsername(actorUsername);
        AuditLog log = AuditLog.builder()
                .actorId(actor.getId())
                .actorUsername(actor.getUsername())
                .tenantId(actor.getTenant() != null ? actor.getTenant().getId() : null)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    public Page<AuditLogResponse> getAuditLogs(Pageable pageable, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        if (authorizationService.isSuperAdmin(currentUser)) {
            return auditLogRepository.findAll(pageable).map(auditLogMapper::toResponse);
        }
        if (currentUser.getTenant() == null) {
            return Page.empty(pageable);
        }
        return auditLogRepository.findAllByTenantId(currentUser.getTenant().getId(), pageable).map(auditLogMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('AUDIT_LOG_READ')")
    public AuditLogResponse getAuditLogById(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        AuditLog log;
        if (authorizationService.isSuperAdmin(currentUser)) {
            log = auditLogRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Audit log not found"));
        } else if (currentUser.getTenant() != null) {
            log = auditLogRepository.findByIdAndTenantId(id, currentUser.getTenant().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Audit log not found"));
        } else {
            throw new AccessDeniedException("Cannot access this audit log");
        }
        return auditLogMapper.toResponse(log);
    }
}
