package com.wedding.planner.dto;

import com.wedding.planner.domain.ExpenseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ExpenseRequest(
        @NotBlank String description,
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotNull ExpenseCategory category,
        boolean paid) {
}
