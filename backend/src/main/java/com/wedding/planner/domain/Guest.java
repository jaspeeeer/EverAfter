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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A guest (or household) invited to a wedding, tracked in the guest-list CRM.
 */
@Entity
@Table(name = "guests")
@SQLRestriction("deleted_at is null")
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    /** Free-text honorific ("Mr.", "Dr.", …); optional. */
    @Column(name = "title", length = 20)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "phone", length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "rsvp_status", nullable = false, length = 20)
    private RsvpStatus rsvpStatus = RsvpStatus.PENDING;

    /**
     * Number of people this entry represents (e.g. a couple or family = a party of 2+).
     * Null means "just this guest" — treated as 1 everywhere it's summed.
     */
    @Column(name = "party_size")
    private Integer partySize;

    @Column(name = "dietary_notes", length = 500)
    private String dietaryNotes;

    /** Seating assignment; null until the couple plans tables. */
    @Column(name = "table_number")
    private Integer tableNumber;

    // --- Planner-internal classification (never exposed on the public RSVP surface) ---

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 1)
    private GuestPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_to", length = 10)
    private RelatedTo relatedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", length = 30)
    private GuestRelationship relationship;

    /**
     * Admin-managed wedding role(s) (Principal Sponsor, Best Man, …); a guest may carry zero,
     * one, or several. Owning side; join rows cascade at the DB level.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "guest_role_assignments",
            joinColumns = @JoinColumn(name = "guest_id",
                    foreignKey = @ForeignKey(name = "fk_gra_guest")),
            inverseJoinColumns = @JoinColumn(name = "role_id",
                    foreignKey = @ForeignKey(name = "fk_gra_role"))
    )
    private Set<GuestRole> roles = new HashSet<>();

    /** Secret token for the public no-login RSVP link. */
    @Column(name = "rsvp_token", nullable = false, unique = true, updatable = false)
    private UUID rsvpToken = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_guests_project")
    )
    private Project project;

    /** Soft-delete tombstone; null means live — see the {@code @SQLRestriction} on this class. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Guest() {
        // Required by JPA.
    }

    public Guest(String firstName, RsvpStatus rsvpStatus, Integer partySize) {
        this.firstName = firstName;
        this.rsvpStatus = rsvpStatus;
        this.partySize = partySize;
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    /** Display name composed as "Title First Last", skipping any unset parts. */
    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title).append(' ');
        }
        sb.append(firstName);
        if (lastName != null && !lastName.isBlank()) {
            sb.append(' ').append(lastName);
        }
        return sb.toString();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public RsvpStatus getRsvpStatus() {
        return rsvpStatus;
    }

    public void setRsvpStatus(RsvpStatus rsvpStatus) {
        this.rsvpStatus = rsvpStatus;
    }

    public Integer getPartySize() {
        return partySize;
    }

    public void setPartySize(Integer partySize) {
        this.partySize = partySize;
    }

    public String getDietaryNotes() {
        return dietaryNotes;
    }

    public void setDietaryNotes(String dietaryNotes) {
        this.dietaryNotes = dietaryNotes;
    }

    public Integer getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(Integer tableNumber) {
        this.tableNumber = tableNumber;
    }

    public GuestPriority getPriority() {
        return priority;
    }

    public void setPriority(GuestPriority priority) {
        this.priority = priority;
    }

    public RelatedTo getRelatedTo() {
        return relatedTo;
    }

    public void setRelatedTo(RelatedTo relatedTo) {
        this.relatedTo = relatedTo;
    }

    public GuestRelationship getRelationship() {
        return relationship;
    }

    public void setRelationship(GuestRelationship relationship) {
        this.relationship = relationship;
    }

    public Set<GuestRole> getRoles() {
        return roles;
    }

    /** Replaces the assigned roles wholesale (PUT semantics). */
    public void replaceRoles(Set<GuestRole> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }

    public UUID getRsvpToken() {
        return rsvpToken;
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
        if (!(o instanceof Guest other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
