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
 * One preset task inside a {@link ChecklistTemplate}.
 */
@Entity
@Table(name = "checklist_template_items")
public class ChecklistTemplateItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    /** When set, applying computes dueDate = project.weddingDate - this many days. */
    @Column(name = "days_before_wedding")
    private Integer daysBeforeWedding;

    /** Position within the template; assigned by {@link ChecklistTemplate#addItem}. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "template_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_cti_template")
    )
    private ChecklistTemplate template;

    protected ChecklistTemplateItem() {
        // Required by JPA.
    }

    public ChecklistTemplateItem(String title, String description, Integer daysBeforeWedding) {
        this.title = title;
        this.description = description;
        this.daysBeforeWedding = daysBeforeWedding;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getDaysBeforeWedding() {
        return daysBeforeWedding;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public ChecklistTemplate getTemplate() {
        return template;
    }

    public void setTemplate(ChecklistTemplate template) {
        this.template = template;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChecklistTemplateItem other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
