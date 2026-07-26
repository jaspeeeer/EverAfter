package com.wedding.planner.service;

import com.wedding.planner.domain.Expense;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.domain.VendorCategory;
import com.wedding.planner.dto.ExpenseRequest;
import com.wedding.planner.dto.ExpenseResponse;
import com.wedding.planner.exception.BadRequestException;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.VendorRepository;
import java.math.BigDecimal;
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
    private final VendorCategoryService vendorCategoryService;
    private final VendorRepository vendorRepository;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ProjectRepository projectRepository,
                          VendorCategoryService vendorCategoryService,
                          VendorRepository vendorRepository) {
        this.expenseRepository = expenseRepository;
        this.projectRepository = projectRepository;
        this.vendorCategoryService = vendorCategoryService;
        this.vendorRepository = vendorRepository;
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
        VendorCategory category = vendorCategoryService.requireForAssignment(request.categoryId());
        Expense expense = new Expense(request.description(), request.amount(), category);
        expense.setPaid(request.paid());
        expense.setPaidAmount(request.paid() ? request.amount() : BigDecimal.ZERO);
        expense.setProject(project);
        expense.setVendor(resolveVendor(projectId, request.vendorId()));
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(UUID projectId, UUID expenseId, ExpenseRequest request) {
        Expense expense = requireExpenseInProject(projectId, expenseId);
        // Managed lines mirror the vendor's agreed price and payments — nothing here is user-editable.
        if (expense.isManaged()) {
            return ExpenseResponse.from(expense);
        }
        expense.setDescription(request.description());
        expense.setAmount(request.amount());
        expense.setCategory(vendorCategoryService.requireForAssignment(request.categoryId()));
        expense.setVendor(resolveVendor(projectId, request.vendorId()));
        expense.setPaid(request.paid());
        expense.setPaidAmount(request.paid() ? request.amount() : BigDecimal.ZERO);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(UUID projectId, UUID expenseId) {
        Expense expense = requireExpenseInProject(projectId, expenseId);
        if (expense.isManaged()) {
            throw new BadRequestException(
                    "This line is from a vendor's agreed price — clear it on the vendor instead");
        }
        expenseRepository.delete(expense);
    }

    /** Resolves an optional vendor mapping, verifying the vendor belongs to this project. */
    private Vendor resolveVendor(UUID projectId, UUID vendorId) {
        if (vendorId == null) {
            return null;
        }
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new BadRequestException("Unknown vendor: " + vendorId));
        if (!vendor.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Vendor does not belong to this project");
        }
        return vendor;
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
