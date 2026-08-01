package com.wedding.planner.web;

import com.wedding.planner.dto.NotificationResponse;
import com.wedding.planner.notification.NotificationService;
import com.wedding.planner.security.AppUserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * In-app notifications feed. Scoped to the calling user by construction — {@code notifications.user_id}
 * is the tenant key, so no {@code @projectSecurity} guard is needed. Cross-user access surfaces
 * as 404, never 403, to avoid existence leakage.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AppUserPrincipal principal,
                                           @RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @RequestParam(required = false) Integer limit) {
        return notificationService.list(principal.getId(), unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return Map.of("count", notificationService.unreadCount(principal.getId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @PathVariable UUID id) {
        notificationService.markRead(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AppUserPrincipal principal) {
        return Map.of("updated", notificationService.markAllRead(principal.getId()));
    }
}
