package com.wedding.planner.dto;

import com.wedding.planner.domain.RsvpStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GuestRequest(
        @NotBlank String name,
        @Email String email,
        String phone,
        @NotNull RsvpStatus rsvpStatus,
        @Min(1) int partySize,
        String dietaryNotes,
        @Min(1) Integer tableNumber) {
}
