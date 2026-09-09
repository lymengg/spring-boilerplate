package com.example.demo.service;

import com.example.demo.dto.ExpenseResponse;

public interface ApprovalService {

    ExpenseResponse approveExpense(Long id, String currentEmail);

    ExpenseResponse rejectExpense(Long id, String currentEmail);
}
