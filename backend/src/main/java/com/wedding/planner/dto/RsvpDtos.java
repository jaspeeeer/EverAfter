package com.wedding.planner.dto;

import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.RsvpStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** DTOs for the public (no-login) RSVP page. */
public final class RsvpDtos {

    private RsvpDtos() {
    }

    /** What an invitee sees when opening their RSVP link. No internal ids are exposed. */
    public record RsvpViewResponse(
            String guestName,
            String projectName,
            LocalDate weddingDate,
            RsvpStatus rsvpStatus,
            int partySize,
            String dietaryNotes) {

        public static RsvpViewResponse from(Guest guest) {
            return new RsvpViewResponse(
                    guest.getName(),
                    guest.getProject().getName(),
                    guest.getProject().getWeddingDate(),
                    guest.getRsvpStatus(),
                    guest.getPartySize(),
                    guest.getDietaryNotes());
        }
    }

    /**
     * Fields an invitee may update about themselves. Party size is deliberately not here — the
     * server always resets it to 1 on public submission; planners manage headcount via the
     * admin-side guest editor.
     */
    public record RsvpUpdateRequest(
            @NotNull RsvpStatus rsvpStatus,
            String dietaryNotes) {
    }
}
