package com.wedding.planner.dto;

import com.wedding.planner.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TaskRequest(
        @NotBlank String title,
        String description,
        @NotNull TaskStatus status,
        LocalDate dueDate) {
}
