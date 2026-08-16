package com.wedding.planner.dto;

import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        LocalDate weddingDate,
        BigDecimal totalBudget,
        UUID plannerId,
        String plannerEmail,
        UUID ownerId,
        String ownerEmail,
        String ceremonyVenueName,
        String ceremonyVenueAddress,
        String receptionVenueName,
        String receptionVenueAddress,
        LocalTime ceremonyTime,
        LocalTime receptionTime,
        boolean allowGuestPartySize,
        Integer maxPartySize,
        UUID coverAttachmentId,
        UUID ceremonyPhotoAttachmentId,
        UUID receptionPhotoAttachmentId,
        String dressCode,
        String attireNotesMen,
        String attireNotesWomen,
        String attirePalette,
        LocalDate rsvpDeadline,
        String kidsPolicy,
        String socialHashtag,
        Instant createdAt,
        Instant updatedAt) {

    public static ProjectResponse from(Project project) {
        User planner = project.getPlanner();
        User owner = project.getOwner();
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getWeddingDate(),
                project.getTotalBudget(),
                planner != null ? planner.getId() : null,
                planner != null ? planner.getEmail() : null,
                owner != null ? owner.getId() : null,
                owner != null ? owner.getEmail() : null,
                project.getCeremonyVenueName(),
                project.getCeremonyVenueAddress(),
                project.getReceptionVenueName(),
                project.getReceptionVenueAddress(),
                project.getCeremonyTime(),
                project.getReceptionTime(),
                project.isAllowGuestPartySize(),
                project.getMaxPartySize(),
                project.getCoverAttachmentId(),
                project.getCeremonyPhotoAttachmentId(),
                project.getReceptionPhotoAttachmentId(),
                project.getDressCode(),
                project.getAttireNotesMen(),
                project.getAttireNotesWomen(),
                project.getAttirePalette(),
                project.getRsvpDeadline(),
                project.getKidsPolicy(),
                project.getSocialHashtag(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
