package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private Long actorId;
    private String actorUsername;
    private Long tenantId;
    private String action;
    private String resourceType;
    private String resourceId;
    private String details;
    private Instant timestamp;
}
