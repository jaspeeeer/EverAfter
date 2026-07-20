package com.wedding.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One slot on the wedding-day run sheet ("Hair & makeup call", "Ceremony", …), optionally linked
 * to the suppliers ({@link Vendor}s) involved in that slot.
 */
@Entity
@Table(name = "timeline_events")
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_timeline_events_project")
    )
    private Project project;

    /** Suppliers involved in this slot. Owning side; join rows cascade at the DB level. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "timeline_event_vendors",
            joinColumns = @JoinColumn(name = "event_id",
                    foreignKey = @ForeignKey(name = "fk_tev_event")),
            inverseJoinColumns = @JoinColumn(name = "vendor_id",
                    foreignKey = @ForeignKey(name = "fk_tev_vendor"))
    )
    private Set<Vendor> vendors = new HashSet<>();

    protected TimelineEvent() {
        // Required by JPA.
    }

    public TimelineEvent(String title, LocalTime startTime) {
        this.title = title;
        this.startTime = startTime;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Set<Vendor> getVendors() {
        return vendors;
    }

    /** Replaces the linked suppliers wholesale (PUT semantics). */
    public void replaceVendors(Set<Vendor> newVendors) {
        vendors.clear();
        vendors.addAll(newVendors);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimelineEvent other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
