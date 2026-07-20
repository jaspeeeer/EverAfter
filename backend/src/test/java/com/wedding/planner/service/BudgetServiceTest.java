package com.wedding.planner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.ExpenseCategory;
import com.wedding.planner.domain.Project;
import com.wedding.planner.dto.BudgetSummaryResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the budget roll-up math.
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private BudgetService budgetService;

    private final UUID projectId = UUID.randomUUID();

    private Expense expense(String amount, boolean paid) {
        Expense e = new Expense("desc", new BigDecimal(amount), ExpenseCategory.OTHER);
        e.setPaid(paid);
        return e;
    }

    private void givenProjectBudget(BigDecimal budget) {
        Project project = org.mockito.Mockito.mock(Project.class);
        when(project.getTotalBudget()).thenReturn(budget);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    }

    @Test
    void summarizesPaidUnpaidAndRemaining() {
        givenProjectBudget(new BigDecimal("10000.00"));
        when(expenseRepository.findByProjectId(projectId))
                .thenReturn(List.of(expense("3000.00", true), expense("2000.00", false)));

        BudgetSummaryResponse summary = budgetService.summarize(projectId);

        assertThat(summary.totalExpenses()).isEqualByComparingTo("5000.00");
        assertThat(summary.totalPaid()).isEqualByComparingTo("3000.00");
        assertThat(summary.totalOutstanding()).isEqualByComparingTo("2000.00");
        assertThat(summary.remaining()).isEqualByComparingTo("5000.00");
        assertThat(summary.overBudget()).isFalse();
    }

    @Test
    void flagsOverBudget() {
        givenProjectBudget(new BigDecimal("4000.00"));
        when(expenseRepository.findByProjectId(projectId))
                .thenReturn(List.of(expense("3000.00", true), expense("2000.00", true)));

        BudgetSummaryResponse summary = budgetService.summarize(projectId);

        assertThat(summary.totalExpenses()).isEqualByComparingTo("5000.00");
        assertThat(summary.remaining()).isEqualByComparingTo("-1000.00");
        assertThat(summary.overBudget()).isTrue();
    }

    @Test
    void handlesNullBudget() {
        givenProjectBudget(null);
        when(expenseRepository.findByProjectId(projectId))
                .thenReturn(List.of(expense("1500.00", false)));

        BudgetSummaryResponse summary = budgetService.summarize(projectId);

        assertThat(summary.totalBudget()).isNull();
        assertThat(summary.remaining()).isNull();
        assertThat(summary.overBudget()).isFalse();
        assertThat(summary.totalExpenses()).isEqualByComparingTo("1500.00");
    }

    @Test
    void handlesNoExpenses() {
        givenProjectBudget(new BigDecimal("2000.00"));
        when(expenseRepository.findByProjectId(projectId)).thenReturn(List.of());

        BudgetSummaryResponse summary = budgetService.summarize(projectId);

        assertThat(summary.totalExpenses()).isEqualByComparingTo("0");
        assertThat(summary.totalPaid()).isEqualByComparingTo("0");
        assertThat(summary.remaining()).isEqualByComparingTo("2000.00");
    }

    @Test
    void throwsWhenProjectMissing() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> budgetService.summarize(projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
