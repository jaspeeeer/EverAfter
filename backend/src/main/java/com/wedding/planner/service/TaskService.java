package com.wedding.planner.service;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Task;
import com.wedding.planner.dto.TaskRequest;
import com.wedding.planner.dto.TaskResponse;
import com.wedding.planner.exception.ResourceNotFoundException;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.TaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD for tasks nested under a project. Every operation is keyed by {@code projectId} and
 * verifies the task actually belongs to that project, so authorization (gated on {@code projectId})
 * cannot be sidestepped by referencing a task id from another project.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ActivityLogService activityLog;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       ActivityLogService activityLog) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.activityLog = activityLog;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(UUID projectId) {
        requireProject(projectId);
        return taskRepository.findByProjectId(projectId).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse create(UUID projectId, TaskRequest request) {
        Project project = requireProject(projectId);
        Task task = new Task(request.title(), request.status());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setProject(project);
        Task saved = taskRepository.save(task);
        activityLog.record(projectId, ActivityEntityType.TASK, saved.getId(),
                ActivityAction.CREATE, "Added task \"" + saved.getTitle() + "\"");
        return TaskResponse.from(saved);
    }

    @Transactional
    public TaskResponse update(UUID projectId, UUID taskId, TaskRequest request) {
        Task task = requireTaskInProject(projectId, taskId);
        boolean statusChanged = task.getStatus() != request.status();
        boolean titleChanged = !task.getTitle().equals(request.title());
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setDueDate(request.dueDate());
        String summary;
        if (statusChanged && !titleChanged) {
            summary = "Moved task \"" + task.getTitle() + "\" to " + task.getStatus().name();
        } else if (titleChanged) {
            summary = "Renamed task to \"" + task.getTitle() + "\"";
        } else {
            summary = "Updated task \"" + task.getTitle() + "\"";
        }
        activityLog.record(projectId, ActivityEntityType.TASK, taskId,
                ActivityAction.UPDATE, summary);
        return TaskResponse.from(task);
    }

    @Transactional
    public void delete(UUID projectId, UUID taskId) {
        Task task = requireTaskInProject(projectId, taskId);
        String title = task.getTitle();
        task.setDeletedAt(Instant.now());
        activityLog.record(projectId, ActivityEntityType.TASK, taskId,
                ActivityAction.DELETE, "Deleted task \"" + title + "\"");
    }

    @Transactional
    public TaskResponse restore(UUID projectId, UUID taskId) {
        if (taskRepository.restore(taskId, projectId) == 0) {
            throw ResourceNotFoundException.of("Task", taskId);
        }
        Task task = requireTaskInProject(projectId, taskId);
        activityLog.record(projectId, ActivityEntityType.TASK, taskId,
                ActivityAction.RESTORE, "Restored task \"" + task.getTitle() + "\"");
        return TaskResponse.from(task);
    }

    private Project requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project", projectId));
    }

    private Task requireTaskInProject(UUID projectId, UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Task", taskId));
        if (!task.getProject().getId().equals(projectId)) {
            throw ResourceNotFoundException.of("Task", taskId);
        }
        return task;
    }
}
