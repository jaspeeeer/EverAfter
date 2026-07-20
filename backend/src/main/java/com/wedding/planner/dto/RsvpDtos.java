package com.wedding.planner.dto;

import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.RsvpStatus;
import jakarta.validation.constraints.Min;
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

    /** Fields an invitee may update about themselves. */
    public record RsvpUpdateRequest(
            @NotNull RsvpStatus rsvpStatus,
            @Min(1) int partySize,
            String dietaryNotes) {
    }
}
