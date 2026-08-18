package com.example.demo.service;

import com.example.demo.dto.ExpenseResponse;

public interface ApprovalService {

    ExpenseResponse approveExpense(Long id, String currentUsername);

    ExpenseResponse rejectExpense(Long id, String currentUsername);
}
