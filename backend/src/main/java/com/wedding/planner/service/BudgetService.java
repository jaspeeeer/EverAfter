package com.wedding.planner.service;

import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.Project;
import com.wedding.planner.dto.BudgetSummaryResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rolls a project's expenses up into a budget summary (planned vs. committed vs. paid).
 */
@Service
public class BudgetService {

    private final ProjectRepository projectRepository;
    private final ExpenseRepository expenseRepository;

    public BudgetService(ProjectRepository projectRepository, ExpenseRepository expenseRepository) {
        this.projectRepository = projectRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public BudgetSummaryResponse summarize(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));

        List<Expense> expenses = expenseRepository.findByProjectId(projectId);

        BigDecimal totalExpenses = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = expenses.stream()
                .filter(Expense::isPaid)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalOutstanding = totalExpenses.subtract(totalPaid);

        BigDecimal budget = project.getTotalBudget();
        BigDecimal remaining = budget != null ? budget.subtract(totalExpenses) : null;
        boolean overBudget = budget != null && totalExpenses.compareTo(budget) > 0;

        return new BudgetSummaryResponse(
                projectId, budget, totalExpenses, totalPaid, totalOutstanding, remaining, overBudget);
    }
}
