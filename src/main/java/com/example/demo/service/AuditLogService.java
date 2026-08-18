package com.example.demo.service;

import com.example.demo.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

    void record(String action, String resourceType, String resourceId, String details, String actorUsername);

    Page<AuditLogResponse> getAuditLogs(Pageable pageable, String currentUsername);

    AuditLogResponse getAuditLogById(Long id, String currentUsername);
}
