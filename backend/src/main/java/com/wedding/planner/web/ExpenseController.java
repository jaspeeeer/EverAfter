package com.wedding.planner.web;

import com.wedding.planner.dto.ExpenseRequest;
import com.wedding.planner.dto.ExpenseResponse;
import com.wedding.planner.service.ExpenseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expenses nested under a project. Access is gated on the owning {@code projectId}.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<ExpenseResponse> list(@PathVariable UUID projectId) {
        return expenseService.list(projectId);
    }

    @PostMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<ExpenseResponse> create(@PathVariable UUID projectId,
                                                  @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.create(projectId, request));
    }

    @PutMapping("/{expenseId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ExpenseResponse update(@PathVariable UUID projectId,
                                  @PathVariable UUID expenseId,
                                  @Valid @RequestBody ExpenseRequest request) {
        return expenseService.update(projectId, expenseId, request);
    }

    @DeleteMapping("/{expenseId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID expenseId) {
        expenseService.delete(projectId, expenseId);
        return ResponseEntity.noContent().build();
    }
}
