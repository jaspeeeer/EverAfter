package com.wedding.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseRequest(
        @NotBlank String description,
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotNull UUID categoryId,
        UUID vendorId,
        boolean paid) {
}
