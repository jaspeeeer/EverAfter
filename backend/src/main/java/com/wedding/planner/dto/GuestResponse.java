package com.wedding.planner.dto;

import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.RsvpStatus;
import java.util.UUID;

public record GuestResponse(
        UUID id,
        String name,
        String email,
        String phone,
        RsvpStatus rsvpStatus,
        int partySize,
        String dietaryNotes,
        Integer tableNumber,
        UUID rsvpToken,
        UUID projectId) {

    public static GuestResponse from(Guest guest) {
        return new GuestResponse(
                guest.getId(),
                guest.getName(),
                guest.getEmail(),
                guest.getPhone(),
                guest.getRsvpStatus(),
                guest.getPartySize(),
                guest.getDietaryNotes(),
                guest.getTableNumber(),
                guest.getRsvpToken(),
                guest.getProject().getId());
    }
}
