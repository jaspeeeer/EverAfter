package com.wedding.planner.dto;

import com.wedding.planner.domain.Expense;
import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        String description,
        BigDecimal amount,
        UUID categoryId,
        String categoryName,
        boolean paid,
        BigDecimal paidAmount,
        UUID projectId,
        UUID vendorId,
        String vendorName,
        boolean managed) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCategory().getId(),
                expense.getCategory().getName(),
                expense.isPaid(),
                expense.getPaidAmount(),
                expense.getProject().getId(),
                expense.getVendor() != null ? expense.getVendor().getId() : null,
                expense.getVendor() != null ? expense.getVendor().getName() : null,
                expense.isManaged());
    }
}
