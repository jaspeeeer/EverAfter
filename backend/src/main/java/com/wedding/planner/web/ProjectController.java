package com.wedding.planner.web;

import com.wedding.planner.dto.BudgetSummaryResponse;
import com.wedding.planner.dto.ProjectRequest;
import com.wedding.planner.dto.ProjectResponse;
import com.wedding.planner.security.AppUserPrincipal;
import com.wedding.planner.service.BudgetService;
import com.wedding.planner.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project endpoints. Fine-grained data isolation is enforced with {@code @PreAuthorize} against
 * {@code @projectSecurity}; listing is role-scoped inside the service.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final BudgetService budgetService;

    public ProjectController(ProjectService projectService, BudgetService budgetService) {
        this.projectService = projectService;
        this.budgetService = budgetService;
    }

    @GetMapping
    public List<ProjectResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return projectService.listVisible(principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER')")
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody ProjectRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.create(request, principal));
    }

    @GetMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return projectService.get(projectId);
    }

    @PutMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse update(@PathVariable UUID projectId,
                                  @Valid @RequestBody ProjectRequest request) {
        return projectService.update(projectId, request);
    }

    @DeleteMapping("/{projectId}")
    @PreAuthorize("@projectSecurity.canManage(#projectId, authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId) {
        projectService.delete(projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/budget")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public BudgetSummaryResponse budget(@PathVariable UUID projectId) {
        return budgetService.summarize(projectId);
    }

    @PostMapping("/{projectId}/cover")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse setCover(@PathVariable UUID projectId,
                                    @RequestPart("file") MultipartFile file,
                                    @AuthenticationPrincipal AppUserPrincipal principal) {
        return projectService.setCover(projectId, file, principal.getId());
    }

    @DeleteMapping("/{projectId}/cover")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse removeCover(@PathVariable UUID projectId) {
        return projectService.removeCover(projectId);
    }

    @PostMapping("/{projectId}/ceremony-photo")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse setCeremonyPhoto(@PathVariable UUID projectId,
                                            @RequestPart("file") MultipartFile file,
                                            @AuthenticationPrincipal AppUserPrincipal principal) {
        return projectService.setCeremonyPhoto(projectId, file, principal.getId());
    }

    @DeleteMapping("/{projectId}/ceremony-photo")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse removeCeremonyPhoto(@PathVariable UUID projectId) {
        return projectService.removeCeremonyPhoto(projectId);
    }

    @PostMapping("/{projectId}/reception-photo")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse setReceptionPhoto(@PathVariable UUID projectId,
                                             @RequestPart("file") MultipartFile file,
                                             @AuthenticationPrincipal AppUserPrincipal principal) {
        return projectService.setReceptionPhoto(projectId, file, principal.getId());
    }

    @DeleteMapping("/{projectId}/reception-photo")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ProjectResponse removeReceptionPhoto(@PathVariable UUID projectId) {
        return projectService.removeReceptionPhoto(projectId);
    }
}
