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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row per create/update/delete on a project-scoped entity. Renders as the "Activity" tab on
 * the project detail page. The {@code actor_email} snapshot lets the log stay readable after the
 * actor user is deleted (the FK is {@code ON DELETE SET NULL}).
 */
@Entity
@Table(name = "activity_log")
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_activity_log_project")
    )
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "actor_user_id",
            foreignKey = @ForeignKey(name = "fk_activity_log_actor")
    )
    private User actor;

    /** Snapshot; safe to render even after {@link #actor} is set to null on user deletion. */
    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private ActivityEntityType entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private ActivityAction action;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    /** Structured payload for future diff UIs; v1 only renders {@link #summary}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ActivityLog() {
        // Required by JPA.
    }

    public ActivityLog(Project project,
                       User actor,
                       String actorEmail,
                       ActivityEntityType entityType,
                       UUID entityId,
                       ActivityAction action,
                       String summary) {
        this.project = project;
        this.actor = actor;
        this.actorEmail = actorEmail;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.summary = summary;
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public User getActor() {
        return actor;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public ActivityEntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public ActivityAction getAction() {
        return action;
    }

    public String getSummary() {
        return summary;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ActivityLog other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
