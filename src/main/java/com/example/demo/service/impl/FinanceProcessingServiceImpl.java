package com.example.demo.service.impl;

import com.example.demo.constants.AuditActions;
import com.example.demo.dto.ExpenseResponse;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.User;
import com.example.demo.mapper.ExpenseMapper;
import com.example.demo.security.service.AuthorizationService;
import com.example.demo.service.AuditLogService;
import com.example.demo.service.ExpenseService;
import com.example.demo.service.FinanceProcessingService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FinanceProcessingServiceImpl implements FinanceProcessingService {

    private final ExpenseService expenseService;
    private final UserService userService;
    private final AuthorizationService authorizationService;
    private final ExpenseMapper expenseMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    @PreAuthorize("hasAuthority('EXPENSE_PROCESS')")
    public ExpenseResponse processExpense(Long id, String currentUsername) {
        User currentUser = userService.getByUsername(currentUsername);
        Expense expense = expenseService.findAccessibleExpense(id, currentUser);
        if (expense.getStatus() != ExpenseStatus.APPROVED) {
            throw new IllegalStateException("Only approved expenses can be processed");
        }
        if (!authorizationService.canProcessExpense(currentUser, expense)) {
            throw new AccessDeniedException("Cannot process this expense");
        }
        expense.setStatus(ExpenseStatus.PROCESSED);
        expense.setProcessedDate(Instant.now());
        expense.setProcessedBy(currentUser);
        Expense processed = expenseService.save(expense);
        auditLogService.record(AuditActions.EXPENSE_PROCESSED, AuditActions.RESOURCE_EXPENSE, String.valueOf(processed.getId()), "Expense processed for payment", currentUsername);
        return expenseMapper.toResponse(processed);
    }
}
