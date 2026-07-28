package com.wedding.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * An admin-managed guest role (Principal Sponsor, Best Man, Officiating Pastor, …). Guests
 * reference it by FK, so renaming a label propagates everywhere and "is it in use?" is checkable.
 * Deleting an in-use role deactivates it (kept for existing guests, hidden from new pickers).
 */
@Entity
@Table(name = "guest_roles")
public class GuestRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 60, unique = true)
    private String name;

    /** Stable machine key (e.g. "BEST_MAN"); never changes even if the display name is edited. */
    @Column(name = "slug", nullable = false, length = 40, updatable = false, unique = true)
    private String slug;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected GuestRole() {
        // Required by JPA.
    }

    public GuestRole(String name, String slug, int sortOrder) {
        this.name = name;
        this.slug = slug;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GuestRole other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
