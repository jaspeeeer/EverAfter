package com.wedding.planner.web;

import com.wedding.planner.dto.NotificationPreferencesDtos.NotificationPreferencesRequest;
import com.wedding.planner.dto.NotificationPreferencesDtos.NotificationPreferencesResponse;
import com.wedding.planner.notification.NotificationService;
import com.wedding.planner.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferencesController {

    private final NotificationService notificationService;

    public NotificationPreferencesController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationPreferencesResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return notificationService.getPreferences(principal.getId());
    }

    @PutMapping
    public NotificationPreferencesResponse update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody NotificationPreferencesRequest request) {
        return notificationService.updatePreferences(principal.getId(), request);
    }
}
