package com.wedding.planner.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A single wedding project — the aggregate root that owns tasks, vendors and expenses.
 *
 * <p>Ownership foreign keys live here:
 * <ul>
 *   <li>{@link #planner} — the managing {@code ROLE_PLANNER} (required, MANY projects per planner).</li>
 *   <li>{@link #owner} — the couple {@code ROLE_USER}. The unique constraint on {@code owner_id}
 *       enforces the "a couple has exactly one project" rule at the database level.</li>
 * </ul>
 */
@Entity
@Table(
        name = "projects",
        uniqueConstraints = @UniqueConstraint(name = "uq_projects_owner", columnNames = "owner_id")
)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "wedding_date")
    private LocalDate weddingDate;

    @Column(name = "total_budget", precision = 12, scale = 2)
    private BigDecimal totalBudget;

    @Column(name = "ceremony_venue_name", length = 200)
    private String ceremonyVenueName;

    @Column(name = "ceremony_venue_address", length = 500)
    private String ceremonyVenueAddress;

    @Column(name = "reception_venue_name", length = 200)
    private String receptionVenueName;

    @Column(name = "reception_venue_address", length = 500)
    private String receptionVenueAddress;

    @Column(name = "ceremony_time")
    private LocalTime ceremonyTime;

    @Column(name = "reception_time")
    private LocalTime receptionTime;

    @Column(name = "allow_guest_party_size", nullable = false)
    private boolean allowGuestPartySize = false;

    @Column(name = "max_party_size")
    private Integer maxPartySize;

    @Column(name = "cover_attachment_id")
    private UUID coverAttachmentId;

    @Column(name = "ceremony_photo_attachment_id")
    private UUID ceremonyPhotoAttachmentId;

    @Column(name = "reception_photo_attachment_id")
    private UUID receptionPhotoAttachmentId;

    @Column(name = "dress_code", length = 200)
    private String dressCode;

    @Column(name = "attire_notes_men", length = 500)
    private String attireNotesMen;

    @Column(name = "attire_notes_women", length = 500)
    private String attireNotesWomen;

    /** Comma-separated hex colors, e.g. {@code "#f4a5a5,#a5c4f4"}; null/blank means unset. */
    @Column(name = "attire_palette", length = 300)
    private String attirePalette;

    @Column(name = "rsvp_deadline")
    private LocalDate rsvpDeadline;

    @Column(name = "kids_policy", length = 300)
    private String kidsPolicy;

    @Column(name = "social_hashtag", length = 100)
    private String socialHashtag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "planner_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_projects_planner")
    )
    private User planner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "owner_id",
            foreignKey = @ForeignKey(name = "fk_projects_owner")
    )
    private User owner;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Vendor> vendors = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Expense> expenses = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Guest> guests = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TimelineEvent> timelineEvents = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EntourageMember> entourageMembers = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Project() {
        // Required by JPA.
    }

    public Project(String name, User planner) {
        this.name = name;
        this.planner = planner;
    }

    // --- Association helpers keep both sides of the relationship consistent ---

    public void addTask(Task task) {
        tasks.add(task);
        task.setProject(this);
    }

    public void removeTask(Task task) {
        tasks.remove(task);
        task.setProject(null);
    }

    public void addVendor(Vendor vendor) {
        vendors.add(vendor);
        vendor.setProject(this);
    }

    public void removeVendor(Vendor vendor) {
        vendors.remove(vendor);
        vendor.setProject(null);
    }

    public void addExpense(Expense expense) {
        expenses.add(expense);
        expense.setProject(this);
    }

    public void removeExpense(Expense expense) {
        expenses.remove(expense);
        expense.setProject(null);
    }

    public void addGuest(Guest guest) {
        guests.add(guest);
        guest.setProject(this);
    }

    public void removeGuest(Guest guest) {
        guests.remove(guest);
        guest.setProject(null);
    }

    public void addEntourageMember(EntourageMember member) {
        entourageMembers.add(member);
        member.setProject(this);
    }

    public void removeEntourageMember(EntourageMember member) {
        entourageMembers.remove(member);
        member.setProject(null);
    }

    // --- Getters / setters ---

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getWeddingDate() {
        return weddingDate;
    }

    public void setWeddingDate(LocalDate weddingDate) {
        this.weddingDate = weddingDate;
    }

    public BigDecimal getTotalBudget() {
        return totalBudget;
    }

    public void setTotalBudget(BigDecimal totalBudget) {
        this.totalBudget = totalBudget;
    }

    public String getCeremonyVenueName() {
        return ceremonyVenueName;
    }

    public void setCeremonyVenueName(String ceremonyVenueName) {
        this.ceremonyVenueName = ceremonyVenueName;
    }

    public String getCeremonyVenueAddress() {
        return ceremonyVenueAddress;
    }

    public void setCeremonyVenueAddress(String ceremonyVenueAddress) {
        this.ceremonyVenueAddress = ceremonyVenueAddress;
    }

    public String getReceptionVenueName() {
        return receptionVenueName;
    }

    public void setReceptionVenueName(String receptionVenueName) {
        this.receptionVenueName = receptionVenueName;
    }

    public String getReceptionVenueAddress() {
        return receptionVenueAddress;
    }

    public void setReceptionVenueAddress(String receptionVenueAddress) {
        this.receptionVenueAddress = receptionVenueAddress;
    }

    public LocalTime getCeremonyTime() {
        return ceremonyTime;
    }

    public void setCeremonyTime(LocalTime ceremonyTime) {
        this.ceremonyTime = ceremonyTime;
    }

    public LocalTime getReceptionTime() {
        return receptionTime;
    }

    public void setReceptionTime(LocalTime receptionTime) {
        this.receptionTime = receptionTime;
    }

    public boolean isAllowGuestPartySize() {
        return allowGuestPartySize;
    }

    public void setAllowGuestPartySize(boolean allowGuestPartySize) {
        this.allowGuestPartySize = allowGuestPartySize;
    }

    public Integer getMaxPartySize() {
        return maxPartySize;
    }

    public void setMaxPartySize(Integer maxPartySize) {
        this.maxPartySize = maxPartySize;
    }

    public UUID getCoverAttachmentId() {
        return coverAttachmentId;
    }

    public void setCoverAttachmentId(UUID coverAttachmentId) {
        this.coverAttachmentId = coverAttachmentId;
    }

    public UUID getCeremonyPhotoAttachmentId() {
        return ceremonyPhotoAttachmentId;
    }

    public void setCeremonyPhotoAttachmentId(UUID ceremonyPhotoAttachmentId) {
        this.ceremonyPhotoAttachmentId = ceremonyPhotoAttachmentId;
    }

    public UUID getReceptionPhotoAttachmentId() {
        return receptionPhotoAttachmentId;
    }

    public void setReceptionPhotoAttachmentId(UUID receptionPhotoAttachmentId) {
        this.receptionPhotoAttachmentId = receptionPhotoAttachmentId;
    }

    public String getDressCode() {
        return dressCode;
    }

    public void setDressCode(String dressCode) {
        this.dressCode = dressCode;
    }

    public String getAttireNotesMen() {
        return attireNotesMen;
    }

    public void setAttireNotesMen(String attireNotesMen) {
        this.attireNotesMen = attireNotesMen;
    }

    public String getAttireNotesWomen() {
        return attireNotesWomen;
    }

    public void setAttireNotesWomen(String attireNotesWomen) {
        this.attireNotesWomen = attireNotesWomen;
    }

    public String getAttirePalette() {
        return attirePalette;
    }

    public void setAttirePalette(String attirePalette) {
        this.attirePalette = attirePalette;
    }

    public LocalDate getRsvpDeadline() {
        return rsvpDeadline;
    }

    public void setRsvpDeadline(LocalDate rsvpDeadline) {
        this.rsvpDeadline = rsvpDeadline;
    }

    public String getKidsPolicy() {
        return kidsPolicy;
    }

    public void setKidsPolicy(String kidsPolicy) {
        this.kidsPolicy = kidsPolicy;
    }

    public String getSocialHashtag() {
        return socialHashtag;
    }

    public void setSocialHashtag(String socialHashtag) {
        this.socialHashtag = socialHashtag;
    }

    public User getPlanner() {
        return planner;
    }

    public void setPlanner(User planner) {
        this.planner = planner;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public List<Vendor> getVendors() {
        return vendors;
    }

    public List<Expense> getExpenses() {
        return expenses;
    }

    public List<Guest> getGuests() {
        return guests;
    }

    public List<TimelineEvent> getTimelineEvents() {
        return timelineEvents;
    }

    public List<EntourageMember> getEntourageMembers() {
        return entourageMembers;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Project other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
