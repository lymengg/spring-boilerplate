package com.example.demo.service.impl;

import com.example.demo.constants.AuditActions;
import com.example.demo.dto.ExpenseResponse;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.User;
import com.example.demo.mapper.ExpenseMapper;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.ApprovalService;
import com.example.demo.service.AuditLogService;
import com.example.demo.service.ExpenseService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ExpenseService expenseService;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final ExpenseMapper expenseMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE')")
    public ExpenseResponse approveExpense(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Expense expense = expenseService.findAccessibleExpense(id, currentUser);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new IllegalStateException("Only pending expenses can be approved");
        }
        if (!authorizationService.canApproveExpense(currentUser, expense)) {
            throw new AccessDeniedException("Cannot approve this expense");
        }
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setDecisionDate(Instant.now());
        expense.setApprovedBy(currentUser);
        Expense approved = expenseService.save(expense);
        auditLogService.record(AuditActions.EXPENSE_APPROVED, AuditActions.RESOURCE_EXPENSE, String.valueOf(approved.getId()), "Expense approved", currentUsername);
        return expenseMapper.toResponse(approved);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('EXPENSE_REJECT')")
    public ExpenseResponse rejectExpense(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Expense expense = expenseService.findAccessibleExpense(id, currentUser);
        if (expense.getStatus() != ExpenseStatus.PENDING) {
            throw new IllegalStateException("Only pending expenses can be rejected");
        }
        if (!authorizationService.canRejectExpense(currentUser, expense)) {
            throw new AccessDeniedException("Cannot reject this expense");
        }
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setDecisionDate(Instant.now());
        expense.setRejectedBy(currentUser);
        Expense rejected = expenseService.save(expense);
        auditLogService.record(AuditActions.EXPENSE_REJECTED, AuditActions.RESOURCE_EXPENSE, String.valueOf(rejected.getId()), "Expense rejected", currentUsername);
        return expenseMapper.toResponse(rejected);
    }
}
