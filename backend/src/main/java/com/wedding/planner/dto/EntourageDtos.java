package com.wedding.planner.dto;

import com.wedding.planner.domain.EntourageMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** DTOs for a project's entourage (wedding party) list. */
public final class EntourageDtos {

    private EntourageDtos() {
    }

    public record EntourageMemberRequest(
            @NotBlank @Size(max = 100) String role,
            @NotBlank @Size(max = 200) String name) {
    }

    /** Planner-facing — carries the id needed for edit/remove/reorder. */
    public record EntourageMemberResponse(UUID id, String role, String name, int sortOrder) {

        public static EntourageMemberResponse from(EntourageMember member) {
            return new EntourageMemberResponse(
                    member.getId(), member.getRole(), member.getName(), member.getSortOrder());
        }
    }

    /** Public (RSVP page) shape — deliberately no id, matching {@code RsvpViewResponse}'s rule. */
    public record PublicEntourageMember(String role, String name) {

        public static PublicEntourageMember from(EntourageMember member) {
            return new PublicEntourageMember(member.getRole(), member.getName());
        }
    }

    /** One (guest, role) pair the picker checked — a guest can be checked under several role groups. */
    public record GuestRoleImportEntry(@NotNull UUID guestId, @NotNull UUID roleId) {
    }

    /** Bulk-add: one entourage row per (guest, role) pair, copying the guest's name and role's name. */
    public record ImportFromGuestsRequest(@NotEmpty List<@Valid GuestRoleImportEntry> entries) {
    }

    /**
     * Counts from an import run. {@code skippedNotEligible} covers both guests with no role and
     * guests whose role isn't marked {@code entourageEligible} — both are "not eligible" from the
     * picker's point of view.
     */
    public record ImportFromGuestsResult(
            int added, int skippedAlreadyPresent, int skippedNotEligible) {
    }
}
