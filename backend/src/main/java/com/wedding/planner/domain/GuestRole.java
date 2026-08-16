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

    /** Whether this role appears in the Entourage settings card's "import from guests" picker. */
    @Column(name = "entourage_eligible", nullable = false)
    private boolean entourageEligible = false;

    /**
     * Set when this role is a sub-role nested under a top-level one (e.g. "Candle" under
     * "Secondary Sponsor"); null for a top-level role. One level of nesting only — enforced in
     * {@code GuestRoleService}, mirroring {@code Vendor.parent}'s package-item pattern.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_id",
            foreignKey = @ForeignKey(name = "fk_guest_roles_parent")
    )
    private GuestRole parent;

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

    public boolean isEntourageEligible() {
        return entourageEligible;
    }

    public void setEntourageEligible(boolean entourageEligible) {
        this.entourageEligible = entourageEligible;
    }

    public GuestRole getParent() {
        return parent;
    }

    public void setParent(GuestRole parent) {
        this.parent = parent;
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
