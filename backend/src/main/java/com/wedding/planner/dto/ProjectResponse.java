package com.wedding.planner.dto;

import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        LocalDate weddingDate,
        BigDecimal totalBudget,
        UUID plannerId,
        String plannerEmail,
        UUID ownerId,
        String ownerEmail,
        Instant createdAt,
        Instant updatedAt) {

    public static ProjectResponse from(Project project) {
        User planner = project.getPlanner();
        User owner = project.getOwner();
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getWeddingDate(),
                project.getTotalBudget(),
                planner != null ? planner.getId() : null,
                planner != null ? planner.getEmail() : null,
                owner != null ? owner.getId() : null,
                owner != null ? owner.getEmail() : null,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
