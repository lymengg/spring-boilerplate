package com.example.demo.service;

import com.example.demo.dto.ExpenseCreateRequest;
import com.example.demo.dto.ExpenseResponse;
import com.example.demo.dto.ExpenseUpdateRequest;
import com.example.demo.entity.Expense;
import com.example.demo.entity.ExpenseStatus;
import com.example.demo.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ExpenseService {

    Page<ExpenseResponse> getExpenses(Pageable pageable, ExpenseStatus status, Long tenantId, Long departmentId, String currentUsername);

    ExpenseResponse getExpenseById(Long id, String currentUsername);

    ExpenseResponse createExpense(ExpenseCreateRequest request, String currentUsername);

    ExpenseResponse updateExpense(Long id, ExpenseUpdateRequest request, String currentUsername);

    ExpenseResponse cancelExpense(Long id, String currentUsername);

    Expense save(Expense expense);

    Expense findAccessibleExpense(Long id, User user);
}
