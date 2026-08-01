package com.wedding.planner.dto;

import com.wedding.planner.domain.ActivityAction;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.domain.ActivityLog;
import java.time.Instant;
import java.util.UUID;

public record ActivityLogResponse(
        UUID id,
        UUID projectId,
        UUID actorUserId,
        String actorEmail,
        ActivityEntityType entityType,
        UUID entityId,
        ActivityAction action,
        String summary,
        Instant createdAt) {

    public static ActivityLogResponse from(ActivityLog row) {
        return new ActivityLogResponse(
                row.getId(),
                row.getProject().getId(),
                row.getActor() != null ? row.getActor().getId() : null,
                row.getActorEmail(),
                row.getEntityType(),
                row.getEntityId(),
                row.getAction(),
                row.getSummary(),
                row.getCreatedAt());
    }
}
