package com.example.demo.dto;

import com.example.demo.entity.ExpenseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private Long id;
    private String title;
    private String description;
    private BigDecimal amount;
    private String category;
    private ExpenseStatus status;
    private Instant submissionDate;
    private Instant decisionDate;
    private Instant processedDate;
    private Long ownerId;
    private String ownerUsername;
    private Long departmentId;
    private String departmentName;
    private Long tenantId;
    private String tenantName;
    private Long approvedById;
    private String approvedByUsername;
    private Long rejectedById;
    private String rejectedByUsername;
    private Long processedById;
    private String processedByUsername;
    private Instant updatedAt;
}
