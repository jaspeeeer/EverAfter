package com.wedding.planner.dto;

import com.wedding.planner.domain.Notification;
import com.wedding.planner.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String linkPath,
        String entityType,
        UUID entityId,
        UUID projectId,
        Instant readAt,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getLinkPath(),
                n.getEntityType(),
                n.getEntityId(),
                n.getProject() != null ? n.getProject().getId() : null,
                n.getReadAt(),
                n.getCreatedAt());
    }
}
