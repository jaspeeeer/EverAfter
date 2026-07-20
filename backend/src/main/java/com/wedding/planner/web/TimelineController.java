package com.wedding.planner.web;

import com.wedding.planner.dto.TimelineDtos.TimelineEventRequest;
import com.wedding.planner.dto.TimelineDtos.TimelineEventResponse;
import com.wedding.planner.service.TimelineService;
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
 * The wedding-day timeline, nested under a project. Everyone with project access may view it
 * (the couple follows the schedule); only planners/admins may shape it.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/timeline")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    @PreAuthorize("@projectSecurity.canAccess(#projectId, authentication)")
    public List<TimelineEventResponse> list(@PathVariable UUID projectId) {
        return timelineService.list(projectId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<TimelineEventResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody TimelineEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timelineService.create(projectId, request));
    }

    @PutMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @projectSecurity.canAccess(#projectId, authentication)")
    public TimelineEventResponse update(@PathVariable UUID projectId,
                                        @PathVariable UUID eventId,
                                        @Valid @RequestBody TimelineEventRequest request) {
        return timelineService.update(projectId, eventId, request);
    }

    @DeleteMapping("/{eventId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID eventId) {
        timelineService.delete(projectId, eventId);
        return ResponseEntity.noContent().build();
    }

    /** Quick-start: seeds the typical wedding-day run when the timeline is empty. */
    @PostMapping("/typical-day")
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER') and @projectSecurity.canAccess(#projectId, authentication)")
    public ResponseEntity<List<TimelineEventResponse>> applyTypicalDay(
            @PathVariable UUID projectId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(timelineService.applyTypicalDay(projectId));
    }
}
