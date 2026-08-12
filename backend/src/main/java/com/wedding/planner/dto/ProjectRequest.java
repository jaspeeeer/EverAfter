package com.wedding.planner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Create/update payload for a project.
 *
 * @param plannerId  optional; honored only for ADMIN callers assigning a managing planner. For a
 *                   PLANNER caller the managing planner is always themselves.
 * @param ownerEmail optional; the couple/user account to attach as the project owner.
 * @param venueName  optional venue name shown on the public invitation page.
 * @param venueAddress optional venue address (used for a directions link on the invitation page).
 * @param ceremonyTime optional ceremony start time (LocalTime, e.g. 15:00).
 * @param receptionTime optional reception start time.
 * @param allowGuestPartySize when true, guests may set their own party size on the public RSVP
 *                            form (default false — server keeps headcount planner-managed).
 * @param maxPartySize optional cap on guest-submitted party size; only enforced when
 *                      {@code allowGuestPartySize} is true.
 */
public record ProjectRequest(
        @NotBlank String name,
        LocalDate weddingDate,
        @PositiveOrZero BigDecimal totalBudget,
        UUID plannerId,
        String ownerEmail,
        @Size(max = 200) String venueName,
        @Size(max = 500) String venueAddress,
        LocalTime ceremonyTime,
        LocalTime receptionTime,
        boolean allowGuestPartySize,
        @Min(1) Integer maxPartySize) {
}
