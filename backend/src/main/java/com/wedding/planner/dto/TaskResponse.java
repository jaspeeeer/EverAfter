package com.wedding.planner.dto;

import com.wedding.planner.domain.Task;
import com.wedding.planner.domain.TaskStatus;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        UUID projectId) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getDueDate(),
                task.getProject().getId());
    }
}
