package com.example.demo.service;

import com.example.demo.dto.ExpenseCreateRequest;
import com.example.demo.dto.ExpenseResponse;
import com.example.demo.dto.ExpenseUpdateRequest;
import com.example.demo.dto.PageResponse;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.User;
import org.springframework.data.domain.Pageable;

public interface ExpenseService {

    PageResponse<ExpenseResponse> getExpenses(Pageable pageable, ExpenseStatus status, Long tenantId, Long departmentId, String currentEmail);

    ExpenseResponse getExpenseById(Long id, String currentEmail);

    ExpenseResponse createExpense(ExpenseCreateRequest request, String currentEmail);

    ExpenseResponse updateExpense(Long id, ExpenseUpdateRequest request, String currentEmail);

    ExpenseResponse cancelExpense(Long id, String currentEmail);

    Expense save(Expense expense);

    Expense findAccessibleExpense(Long id, User user);
}
