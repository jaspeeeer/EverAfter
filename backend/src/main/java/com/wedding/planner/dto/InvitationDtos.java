package com.wedding.planner.dto;

import com.wedding.planner.domain.Invitation;
import com.wedding.planner.domain.InvitationStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

/** DTOs for the couple-invitation flow. */
public final class InvitationDtos {

    private InvitationDtos() {
    }

    public record InvitationRequest(@NotBlank @Email String email) {
    }

    /** Full view for the managing planner (includes the secret token to build the link). */
    public record InvitationResponse(
            UUID id,
            String email,
            UUID token,
            InvitationStatus status,
            UUID projectId,
            Instant createdAt,
            Instant acceptedAt) {

        public static InvitationResponse from(Invitation invitation) {
            return new InvitationResponse(
                    invitation.getId(),
                    invitation.getEmail(),
                    invitation.getToken(),
                    invitation.getStatus(),
                    invitation.getProject().getId(),
                    invitation.getCreatedAt(),
                    invitation.getAcceptedAt());
        }
    }

    /** Public view shown on the register page (no ids beyond what the invitee needs). */
    public record InvitationPublicResponse(
            String email,
            String projectName,
            InvitationStatus status) {

        public static InvitationPublicResponse from(Invitation invitation) {
            return new InvitationPublicResponse(
                    invitation.getEmail(),
                    invitation.getProject().getName(),
                    invitation.getStatus());
        }
    }
}
