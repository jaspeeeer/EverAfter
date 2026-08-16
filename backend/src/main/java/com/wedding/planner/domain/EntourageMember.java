package com.wedding.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * One named entry in a project's entourage (wedding party) — e.g. "Best Man" / "Juan Dela Cruz".
 * A simple project-scoped ordered list, not a full CRM resource like {@link Guest}: no RSVP, no
 * contact info, no soft delete.
 */
@Entity
@Table(name = "entourage_members")
public class EntourageMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "role", nullable = false, length = 100)
    private String role;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_entourage_members_project")
    )
    private Project project;

    protected EntourageMember() {
        // Required by JPA.
    }

    public EntourageMember(String role, String name, int sortOrder) {
        this.role = role;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntourageMember other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
