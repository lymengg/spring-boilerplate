package com.example.demo.mapper;

import com.example.demo.dto.AuditLogResponse;
import com.example.demo.entity.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorId(log.getActorId())
                .actorUsername(log.getActorUsername())
                .tenantId(log.getTenantId())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .timestamp(log.getTimestamp())
                .build();
    }
}
