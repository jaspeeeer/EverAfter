package com.wedding.planner.dto;

import com.wedding.planner.domain.GuestRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** DTOs for the admin-managed guest role lookup. */
public final class GuestRoleDtos {

    private GuestRoleDtos() {
    }

    public record GuestRoleResponse(
            UUID id, String name, String slug, boolean active, boolean entourageEligible,
            UUID parentId, String parentName) {

        public static GuestRoleResponse from(GuestRole role) {
            GuestRole parent = role.getParent();
            return new GuestRoleResponse(
                    role.getId(), role.getName(), role.getSlug(), role.isActive(),
                    role.isEntourageEligible(),
                    parent != null ? parent.getId() : null,
                    parent != null ? parent.getName() : null);
        }
    }

    /**
     * Create only needs a name (slug is derived); entourageEligible defaults false if omitted.
     * {@code parentId} is optional — null means top-level; when set, it must itself be a
     * top-level role (one level of nesting only, enforced in {@code GuestRoleService}).
     */
    public record CreateGuestRoleRequest(
            @NotBlank @Size(max = 60) String name,
            boolean entourageEligible,
            UUID parentId) {
    }

    /**
     * Update can rename, (de)activate, toggle entourage-picker eligibility, and reparent
     * ({@code parentId} null moves a sub-role back to top-level).
     */
    public record UpdateGuestRoleRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Boolean active,
            @NotNull Boolean entourageEligible,
            UUID parentId) {
    }
}
