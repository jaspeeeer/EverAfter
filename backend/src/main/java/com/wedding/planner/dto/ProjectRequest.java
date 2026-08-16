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
 * @param ceremonyVenueName optional ceremony (church) location name shown on the invitation page.
 * @param ceremonyVenueAddress optional ceremony address (directions link + embedded map).
 * @param receptionVenueName optional reception (venue) location name.
 * @param receptionVenueAddress optional reception address (directions link + embedded map).
 * @param ceremonyTime optional ceremony start time (LocalTime, e.g. 15:00).
 * @param receptionTime optional reception start time.
 * @param allowGuestPartySize when true, guests may set their own party size on the public RSVP
 *                            form (default false — server keeps headcount planner-managed).
 * @param maxPartySize optional cap on guest-submitted party size; only enforced when
 *                      {@code allowGuestPartySize} is true.
 * @param dressCode optional short dress-code label (e.g. "Garden party formal").
 * @param attireNotesMen optional free-text attire guidance for men.
 * @param attireNotesWomen optional free-text attire guidance for women.
 * @param attirePalette optional comma-separated hex color list, e.g. {@code "#f4a5a5,#a5c4f4"}.
 * @param rsvpDeadline optional "please RSVP by" date shown on the invitation.
 * @param kidsPolicy optional free-text note (e.g. "Adults-only celebration").
 * @param socialHashtag optional social-media hashtag, without the leading {@code #}.
 */
public record ProjectRequest(
        @NotBlank String name,
        LocalDate weddingDate,
        @PositiveOrZero BigDecimal totalBudget,
        UUID plannerId,
        String ownerEmail,
        @Size(max = 200) String ceremonyVenueName,
        @Size(max = 500) String ceremonyVenueAddress,
        @Size(max = 200) String receptionVenueName,
        @Size(max = 500) String receptionVenueAddress,
        LocalTime ceremonyTime,
        LocalTime receptionTime,
        boolean allowGuestPartySize,
        @Min(1) Integer maxPartySize,
        @Size(max = 200) String dressCode,
        @Size(max = 500) String attireNotesMen,
        @Size(max = 500) String attireNotesWomen,
        @Size(max = 300) String attirePalette,
        LocalDate rsvpDeadline,
        @Size(max = 300) String kidsPolicy,
        @Size(max = 100) String socialHashtag) {
}
