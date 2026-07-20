package com.wedding.planner.dto;

import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.ExpenseCategory;
import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String description,
        BigDecimal amount,
        ExpenseCategory category,
        boolean paid,
        UUID projectId) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCategory(),
                expense.isPaid(),
                expense.getProject().getId());
    }
}
