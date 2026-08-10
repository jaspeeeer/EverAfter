package com.wedding.planner.dto;

import com.wedding.planner.domain.Attachment;
import com.wedding.planner.domain.AttachmentOwnerType;
import java.time.Instant;
import java.util.UUID;

public final class AttachmentDtos {

    private AttachmentDtos() {}

    public record AttachmentResponse(
            UUID id,
            UUID projectId,
            AttachmentOwnerType ownerType,
            UUID ownerId,
            String filename,
            String contentType,
            long sizeBytes,
            UUID uploadedById,
            String uploadedByEmail,
            Instant uploadedAt) {

        public static AttachmentResponse from(Attachment a) {
            return new AttachmentResponse(
                    a.getId(),
                    a.getProject().getId(),
                    a.getOwnerType(),
                    a.getOwnerId(),
                    a.getFilename(),
                    a.getContentType(),
                    a.getSizeBytes(),
                    a.getUploadedBy() != null ? a.getUploadedBy().getId() : null,
                    a.getUploadedBy() != null ? a.getUploadedBy().getEmail() : null,
                    a.getUploadedAt());
        }
    }
}
