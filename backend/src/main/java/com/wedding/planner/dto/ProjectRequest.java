package com.wedding.planner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Create/update payload for a project.
 *
 * @param plannerId  optional; honored only for ADMIN callers assigning a managing planner. For a
 *                   PLANNER caller the managing planner is always themselves.
 * @param ownerEmail optional; the couple/user account to attach as the project owner.
 */
public record ProjectRequest(
        @NotBlank String name,
        LocalDate weddingDate,
        @PositiveOrZero BigDecimal totalBudget,
        UUID plannerId,
        String ownerEmail) {
}
