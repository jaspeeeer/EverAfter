package com.wedding.planner.domain;

/**
 * Which project-scoped entity an attachment is hung off.
 *
 * <p>{@link #PROJECT} is a special case: a project has at most one attachment <em>per named
 * slot</em> — cover, ceremony photo, reception photo — each tracked via its own FK column
 * ({@code projects.cover_attachment_id}, {@code ceremony_photo_attachment_id},
 * {@code reception_photo_attachment_id}). All three slots share this one owner type since
 * {@code ownerId} is always the project itself regardless of slot; the FK column is what
 * distinguishes them, not the owner type. Every other owner type can have any number of
 * attachments.
 */
public enum AttachmentOwnerType {
    VENDOR,
    VENDOR_PAYMENT,
    EXPENSE,
    PROJECT
}
