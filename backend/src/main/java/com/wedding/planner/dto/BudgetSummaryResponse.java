package com.wedding.planner.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Rolled-up budget figures for a project.
 *
 * @param totalBudget     the project's planned budget (may be {@code null} if unset)
 * @param totalExpenses   sum of every expense line
 * @param totalPaid       sum of expenses marked paid
 * @param totalOutstanding sum of expenses not yet paid
 * @param remaining       {@code totalBudget - totalExpenses} (null when no budget is set)
 * @param overBudget      {@code true} when expenses exceed a set budget
 */
public record BudgetSummaryResponse(
        UUID projectId,
        BigDecimal totalBudget,
        BigDecimal totalExpenses,
        BigDecimal totalPaid,
        BigDecimal totalOutstanding,
        BigDecimal remaining,
        boolean overBudget) {
}
