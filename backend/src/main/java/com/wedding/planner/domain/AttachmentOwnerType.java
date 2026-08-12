package com.wedding.planner.domain;

/**
 * Which project-scoped entity an attachment is hung off.
 *
 * <p>{@link #PROJECT} is a special case: a project has at most one such attachment (its cover
 * photo), tracked via {@code projects.cover_attachment_id} — every other owner type can have any
 * number of attachments.
 */
public enum AttachmentOwnerType {
    VENDOR,
    VENDOR_PAYMENT,
    EXPENSE,
    PROJECT
}
