package com.example.demo.mapper;

import com.example.demo.dto.ExpenseResponse;
import com.example.demo.entity.Expense;
import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper {

    public ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .title(expense.getTitle())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .category(expense.getCategory())
                .status(expense.getStatus())
                .submissionDate(expense.getSubmissionDate())
                .decisionDate(expense.getDecisionDate())
                .processedDate(expense.getProcessedDate())
                .ownerId(expense.getOwner() != null ? expense.getOwner().getId() : null)
                .ownerUsername(expense.getOwner() != null ? expense.getOwner().getUsername() : null)
                .departmentId(expense.getDepartment() != null ? expense.getDepartment().getId() : null)
                .departmentName(expense.getDepartment() != null ? expense.getDepartment().getName() : null)
                .tenantId(expense.getTenant() != null ? expense.getTenant().getId() : null)
                .tenantName(expense.getTenant() != null ? expense.getTenant().getName() : null)
                .approvedById(expense.getApprovedBy() != null ? expense.getApprovedBy().getId() : null)
                .approvedByUsername(expense.getApprovedBy() != null ? expense.getApprovedBy().getUsername() : null)
                .rejectedById(expense.getRejectedBy() != null ? expense.getRejectedBy().getId() : null)
                .rejectedByUsername(expense.getRejectedBy() != null ? expense.getRejectedBy().getUsername() : null)
                .processedById(expense.getProcessedBy() != null ? expense.getProcessedBy().getId() : null)
                .processedByUsername(expense.getProcessedBy() != null ? expense.getProcessedBy().getUsername() : null)
                .updatedAt(expense.getUpdatedAt())
                .build();
    }
}
