package com.wedding.planner.dto;

import com.wedding.planner.domain.Gender;
import com.wedding.planner.domain.GuestPriority;
import com.wedding.planner.domain.GuestRelationship;
import com.wedding.planner.domain.RelatedTo;
import com.wedding.planner.domain.RsvpStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record GuestRequest(
        @NotBlank String firstName,
        String lastName,
        String title,
        Gender gender,
        @Email String email,
        String phone,
        @NotNull RsvpStatus rsvpStatus,
        @Min(1) Integer partySize,
        String dietaryNotes,
        @Min(1) Integer tableNumber,
        // Planner-internal classification — all optional.
        GuestPriority priority,
        RelatedTo relatedTo,
        GuestRelationship relationship,
        List<UUID> roleIds) {
}
