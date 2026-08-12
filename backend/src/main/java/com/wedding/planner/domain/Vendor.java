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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A supplier being considered or booked for a wedding.
 */
@Entity
@Table(name = "vendors")
@SQLRestriction("deleted_at is null")
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vendors_category")
    )
    private VendorCategory category;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "booked", nullable = false)
    private boolean booked = false;

    /** The agreed ("done deal") price. When set, a linked budget expense is kept in sync. */
    @Column(name = "agreed_price", precision = 12, scale = 2)
    private BigDecimal agreedPrice;

    /** Optional link back to the global directory entry this vendor was added from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "directory_id",
            foreignKey = @ForeignKey(name = "fk_vendors_directory")
    )
    private VendorDirectoryEntry directoryEntry;

    /**
     * Set when this vendor is a component item nested under a package (e.g. a coordinator
     * package bundling catering + photography); null for a top-level vendor. A package's price,
     * payments, and managed budget line live only on the top-level vendor — see
     * {@code VendorService.syncVendorExpense}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "parent_id",
            foreignKey = @ForeignKey(name = "fk_vendors_parent")
    )
    private Vendor parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vendors_project")
    )
    private Project project;

    /** Soft-delete tombstone; null means live — see the {@code @SQLRestriction} on this class. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Vendor() {
        // Required by JPA.
    }

    public Vendor(String name, VendorCategory category) {
        this.name = name;
        this.category = category;
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

    public VendorCategory getCategory() {
        return category;
    }

    public void setCategory(VendorCategory category) {
        this.category = category;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isBooked() {
        return booked;
    }

    public void setBooked(boolean booked) {
        this.booked = booked;
    }

    public BigDecimal getAgreedPrice() {
        return agreedPrice;
    }

    public void setAgreedPrice(BigDecimal agreedPrice) {
        this.agreedPrice = agreedPrice;
    }

    public VendorDirectoryEntry getDirectoryEntry() {
        return directoryEntry;
    }

    public void setDirectoryEntry(VendorDirectoryEntry directoryEntry) {
        this.directoryEntry = directoryEntry;
    }

    public Vendor getParent() {
        return parent;
    }

    public void setParent(Vendor parent) {
        this.parent = parent;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vendor other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
