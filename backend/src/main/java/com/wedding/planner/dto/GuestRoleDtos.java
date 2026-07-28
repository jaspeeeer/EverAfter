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

    public record GuestRoleResponse(UUID id, String name, String slug, boolean active) {

        public static GuestRoleResponse from(GuestRole role) {
            return new GuestRoleResponse(
                    role.getId(), role.getName(), role.getSlug(), role.isActive());
        }
    }

    /** Create only needs a name (slug is derived). */
    public record CreateGuestRoleRequest(@NotBlank @Size(max = 60) String name) {
    }

    /** Update can rename and/or (de)activate. */
    public record UpdateGuestRoleRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull Boolean active) {
    }
}
