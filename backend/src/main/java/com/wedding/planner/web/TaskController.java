package com.wedding.planner.web;

import com.wedding.planner.dto.TaskRequest;
import com.wedding.planner.dto.TaskResponse;
import com.wedding.planner.dto.TemplateDtos.ApplyTemplateRequest;
import com.wedding.planner.service.TaskService;
import com.wedding.planner.service.TemplateService;
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
 * Tasks nested under a project. Access is gated on the owning {@code projectId}, so isolation is
 * inherited from the project's access rules.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TemplateService templateService;

    public TaskController(TaskService taskService, TemplateService templateService) {
        this.taskService = taskService;
        this.templateService = templateService;
    }

    /**
     * Bulk-creates tasks from a checklist template. Planners/admins only (couples cannot apply
     * templates), and only on projects the caller can access.
     */
    @PostMapping("/apply-template")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<List<TaskResponse>> applyTemplate(
            @PathVariable UUID projectId,
            @Valid @RequestBody ApplyTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.applyChecklistTemplate(projectId, request.templateId()));
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<TaskResponse> list(@PathVariable UUID projectId) {
        return taskService.list(projectId);
    }

    @PostMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<TaskResponse> create(@PathVariable UUID projectId,
                                               @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(projectId, request));
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public TaskResponse update(@PathVariable UUID projectId,
                               @PathVariable UUID taskId,
                               @Valid @RequestBody TaskRequest request) {
        return taskService.update(projectId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID taskId) {
        taskService.delete(projectId, taskId);
        return ResponseEntity.noContent().build();
    }
}
