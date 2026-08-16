package com.wedding.planner.dto;

import com.wedding.planner.domain.EntourageMember;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
}
