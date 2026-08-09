package com.wedding.planner.dto;

import com.wedding.planner.domain.Gender;
import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.GuestPriority;
import com.wedding.planner.domain.GuestRelationship;
import com.wedding.planner.domain.RelatedTo;
import com.wedding.planner.domain.RsvpStatus;
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
        UUID roleId,
        String roleName,
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
                guest.getRole() != null ? guest.getRole().getId() : null,
                guest.getRole() != null ? guest.getRole().getName() : null,
                guest.getRsvpToken(),
                guest.getProject().getId());
    }
}
