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
import java.util.Objects;
import java.util.UUID;

/**
 * One preset vendor slot inside a {@link VendorTemplate}.
 */
@Entity
@Table(name = "vendor_template_items")
public class VendorTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private VendorCategory category = VendorCategory.OTHER;

    /** Position within the template; assigned by {@link VendorTemplate#addItem}. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "template_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_vti_template")
    )
    private VendorTemplate template;

    protected VendorTemplateItem() {
        // Required by JPA.
    }

    public VendorTemplateItem(String name, VendorCategory category) {
        this.name = name;
        this.category = category;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public VendorCategory getCategory() {
        return category;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public VendorTemplate getTemplate() {
        return template;
    }

    public void setTemplate(VendorTemplate template) {
        this.template = template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VendorTemplateItem other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
