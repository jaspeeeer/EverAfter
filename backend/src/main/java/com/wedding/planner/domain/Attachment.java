package com.wedding.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A file attached to a vendor, vendor payment, or expense. Owner identity is polymorphic —
 * ({@link #ownerType}, {@link #ownerId}) — because a single SQL column can't FK to three tables;
 * see {@code V16__attachments.sql}. {@link #project} is denormalised so RBAC checks stay a cheap
 * indexed lookup and ON DELETE CASCADE from {@code projects} handles project-level cleanup.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_attachments_project")
    )
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 24)
    private AttachmentOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Opaque key inside the {@code AttachmentStorage} backend — never a user-supplied path. */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "uploaded_by",
            foreignKey = @ForeignKey(name = "fk_attachments_uploader")
    )
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected Attachment() {
        // Required by JPA.
    }

    public Attachment(Project project,
                      AttachmentOwnerType ownerType,
                      UUID ownerId,
                      String filename,
                      String contentType,
                      long sizeBytes,
                      String storageKey,
                      User uploadedBy) {
        this.project = project;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public AttachmentOwnerType getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStorageKey() { return storageKey; }
    public User getUploadedBy() { return uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Attachment other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
