package com.wedding.planner.service;

import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.Project;
import com.wedding.planner.dto.ExpenseRequest;
import com.wedding.planner.dto.ExpenseResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for expenses nested under a project. See {@link TaskService} for the project-scoping
 * pattern that keeps authorization sound.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;

    public ExpenseService(ExpenseRepository expenseRepository, ProjectRepository projectRepository) {
        this.expenseRepository = expenseRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(UUID projectId) {
        requireProject(projectId);
        return expenseRepository.findByProjectId(projectId).stream()
                .map(ExpenseResponse::from)
                .toList();
    }

    @Transactional
    public ExpenseResponse create(UUID projectId, ExpenseRequest request) {
        Project project = requireProject(projectId);
        Expense expense = new Expense(request.description(), request.amount(), request.category());
        expense.setPaid(request.paid());
        expense.setProject(project);
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(UUID projectId, UUID expenseId, ExpenseRequest request) {
        Expense expense = requireExpenseInProject(projectId, expenseId);
        expense.setDescription(request.description());
        expense.setAmount(request.amount());
        expense.setCategory(request.category());
        expense.setPaid(request.paid());
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(UUID projectId, UUID expenseId) {
        Expense expense = requireExpenseInProject(projectId, expenseId);
        expenseRepository.delete(expense);
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private Expense requireExpenseInProject(UUID projectId, UUID expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Expense", expenseId));
        if (!expense.getProject().getId().equals(projectId)) {
            throw ResourceNotFoundException.of("Expense", expenseId);
        }
        return expense;
    }
}
