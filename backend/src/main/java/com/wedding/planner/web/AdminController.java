package com.wedding.planner.web;

import com.wedding.planner.dto.AdminDtos.AdminUserResponse;
import com.wedding.planner.dto.AdminDtos.PlatformStatsResponse;
import com.wedding.planner.dto.AdminDtos.UpdateEnabledRequest;
import com.wedding.planner.security.AppUserPrincipal;
import com.wedding.planner.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform administration endpoints, restricted to {@code ROLE_ADMIN}.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public List<AdminUserResponse> users() {
        return adminService.listUsers();
    }

    @PutMapping("/users/{userId}/enabled")
    public AdminUserResponse setEnabled(@PathVariable UUID userId,
                                        @Valid @RequestBody UpdateEnabledRequest request,
                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        return adminService.setEnabled(userId, request.enabled(), principal.getId());
    }

    @GetMapping("/stats")
    public PlatformStatsResponse stats() {
        return adminService.stats();
    }
}
