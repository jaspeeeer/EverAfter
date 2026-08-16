package com.wedding.planner.dto;

import com.wedding.planner.domain.Guest;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.RsvpStatus;
import com.wedding.planner.dto.EntourageDtos.PublicEntourageMember;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** DTOs for the public (no-login) RSVP page. */
public final class RsvpDtos {

    private RsvpDtos() {
    }

    /**
     * What an invitee sees when opening their RSVP link. No internal ids are exposed.
     *
     * <p>Venue/time fields are safe to expose publicly — they are exactly what an invitation is
     * supposed to communicate. Internal planner fields (guest classification, tokens, budget) stay
     * off this DTO.
     */
    public record RsvpViewResponse(
            String guestName,
            String projectName,
            LocalDate weddingDate,
            RsvpStatus rsvpStatus,
            int partySize,
            String dietaryNotes,
            String ceremonyVenueName,
            String ceremonyVenueAddress,
            String receptionVenueName,
            String receptionVenueAddress,
            LocalTime ceremonyTime,
            LocalTime receptionTime,
            boolean allowGuestPartySize,
            Integer maxPartySize,
            boolean hasCover,
            boolean hasCeremonyPhoto,
            boolean hasReceptionPhoto,
            String dressCode,
            String attireNotesMen,
            String attireNotesWomen,
            String attirePalette,
            LocalDate rsvpDeadline,
            String kidsPolicy,
            String socialHashtag,
            List<PublicEntourageMember> entourage) {

        /** @param entourage ordered, no ids — see {@link PublicEntourageMember}. */
        public static RsvpViewResponse from(Guest guest, List<PublicEntourageMember> entourage) {
            Project project = guest.getProject();
            return new RsvpViewResponse(
                    guest.getFullName(),
                    project.getName(),
                    project.getWeddingDate(),
                    guest.getRsvpStatus(),
                    guest.getPartySize() != null ? guest.getPartySize() : 1,
                    guest.getDietaryNotes(),
                    project.getCeremonyVenueName(),
                    project.getCeremonyVenueAddress(),
                    project.getReceptionVenueName(),
                    project.getReceptionVenueAddress(),
                    project.getCeremonyTime(),
                    project.getReceptionTime(),
                    project.isAllowGuestPartySize(),
                    project.getMaxPartySize(),
                    project.getCoverAttachmentId() != null,
                    project.getCeremonyPhotoAttachmentId() != null,
                    project.getReceptionPhotoAttachmentId() != null,
                    project.getDressCode(),
                    project.getAttireNotesMen(),
                    project.getAttireNotesWomen(),
                    project.getAttirePalette(),
                    project.getRsvpDeadline(),
                    project.getKidsPolicy(),
                    project.getSocialHashtag(),
                    entourage);
        }
    }

    /**
     * Fields an invitee may update about themselves.
     *
     * <p>{@code partySize} is optional and only honored when the project has opted in
     * ({@code allowGuestPartySize}) — see {@code GuestService#respondByRsvpToken}. When the
     * project hasn't opted in, the field is ignored entirely and the guest's existing party size
     * is left untouched (planners manage headcount via the admin-side guest editor either way).
     */
    public record RsvpUpdateRequest(
            @NotNull RsvpStatus rsvpStatus,
            String dietaryNotes,
            @Min(1) Integer partySize) {
    }
}
