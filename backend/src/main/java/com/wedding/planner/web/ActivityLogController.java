package com.wedding.planner.web;

import com.wedding.planner.audit.ActivityLogService;
import com.wedding.planner.domain.ActivityEntityType;
import com.wedding.planner.dto.ActivityLogResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only activity feed for a project. Access mirrors other project tabs
 * ({@code @projectSecurity.canAccess}) so admins, the managing planner, and the couple all see
 * the same feed.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/activity")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<ActivityLogResponse> feed(
            @PathVariable UUID projectId,
            @RequestParam(required = false) ActivityEntityType entityType,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) Instant cursorCreatedAt,
            @RequestParam(required = false) UUID cursorId,
            @RequestParam(required = false) Integer limit) {
        return activityLogService.feed(
                projectId, entityType, actorId, cursorCreatedAt, cursorId, limit);
    }
}
