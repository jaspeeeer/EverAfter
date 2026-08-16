package com.wedding.planner.dto;

import com.wedding.planner.domain.Gender;
import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.GuestPriority;
import com.wedding.planner.domain.GuestRelationship;
import com.wedding.planner.domain.GuestRole;
import com.wedding.planner.domain.RelatedTo;
import com.wedding.planner.domain.RsvpStatus;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record GuestResponse(
        UUID id,
        String firstName,
        String lastName,
        String title,
        Gender gender,
        String email,
        String phone,
        RsvpStatus rsvpStatus,
        Integer partySize,
        String dietaryNotes,
        Integer tableNumber,
        GuestPriority priority,
        RelatedTo relatedTo,
        GuestRelationship relationship,
        List<GuestRoleAssignmentResponse> roles,
        UUID rsvpToken,
        UUID projectId) {

    public static GuestResponse from(Guest guest) {
        return new GuestResponse(
                guest.getId(),
                guest.getFirstName(),
                guest.getLastName(),
                guest.getTitle(),
                guest.getGender(),
                guest.getEmail(),
                guest.getPhone(),
                guest.getRsvpStatus(),
                guest.getPartySize(),
                guest.getDietaryNotes(),
                guest.getTableNumber(),
                guest.getPriority(),
                guest.getRelatedTo(),
                guest.getRelationship(),
                guest.getRoles().stream()
                        .map(GuestRoleAssignmentResponse::from)
                        .sorted(Comparator.comparing(GuestRoleAssignmentResponse::name))
                        .toList(),
                guest.getRsvpToken(),
                guest.getProject().getId());
    }

    /** One role assignment on a guest — mirrors TimelineDtos.EventVendorResponse. */
    record GuestRoleAssignmentResponse(UUID id, String name, boolean entourageEligible, String parentName) {

        static GuestRoleAssignmentResponse from(GuestRole role) {
            return new GuestRoleAssignmentResponse(
                    role.getId(),
                    role.getName(),
                    role.isEntourageEligible(),
                    role.getParent() != null ? role.getParent().getName() : null);
        }
    }
}
