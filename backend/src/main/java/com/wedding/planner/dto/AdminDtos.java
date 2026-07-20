package com.wedding.planner.dto;

import com.wedding.planner.domain.User;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs for the admin dashboard. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            List<String> roles,
            boolean enabled) {

        public static AdminUserResponse from(User user) {
            return new AdminUserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getRoles().stream().map(r -> r.getName().name()).toList(),
                    user.isEnabled());
        }
    }

    public record UpdateEnabledRequest(@NotNull Boolean enabled) {
    }

    /**
     * Platform-wide counters for the admin dashboard.
     *
     * @param usersByRole e.g. {"ROLE_PLANNER": 12, "ROLE_USER": 30, "ROLE_ADMIN": 1}
     */
    public record PlatformStatsResponse(
            long totalUsers,
            Map<String, Long> usersByRole,
            long totalProjects,
            long totalTasks,
            long totalVendors,
            long totalExpenses,
            long totalGuests) {
    }
}
