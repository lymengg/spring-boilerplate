package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ExpenseCreateRequest;
import com.example.demo.dto.ExpenseResponse;
import com.example.demo.dto.ExpenseUpdateRequest;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.service.ApprovalService;
import com.example.demo.service.ExpenseService;
import com.example.demo.service.FinanceProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ApprovalService approvalService;
    private final FinanceProcessingService financeProcessingService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ExpenseResponse>>> getExpenses(
            Pageable pageable,
            @RequestParam(required = false) ExpenseStatus status,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) Long departmentId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expenses retrieved",
                expenseService.getExpenses(pageable, status, tenantId, departmentId, authentication.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> getExpenseById(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expense retrieved", expenseService.getExpenseById(id, authentication.getName())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(@Valid @RequestBody ExpenseCreateRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expense created", expenseService.createExpense(request, authentication.getName())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(@PathVariable Long id, @Valid @RequestBody ExpenseUpdateRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expense updated", expenseService.updateExpense(id, request, authentication.getName())));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ExpenseResponse>> cancelExpense(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expense cancelled", expenseService.cancelExpense(id, authentication.getName())));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ExpenseResponse>> approveExpense(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expense approved", approvalService.approveExpense(id, authentication.getName())));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ExpenseResponse>> rejectExpense(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expense rejected", approvalService.rejectExpense(id, authentication.getName())));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<ApiResponse<ExpenseResponse>> processExpense(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Expense processed", financeProcessingService.processExpense(id, authentication.getName())));
    }
}
