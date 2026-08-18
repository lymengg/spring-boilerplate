package com.example.demo.service;

import com.example.demo.dto.ExpenseResponse;

public interface FinanceProcessingService {

    ExpenseResponse processExpense(Long id, String currentUsername);
}
