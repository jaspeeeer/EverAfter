package com.wedding.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wedding.planner.AbstractPostgresContainerTest;
import com.wedding.planner.repository.GuestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Verifies Project -> Guest mapping: RSVP enum round-tripping, cascade on delete, and the
 * project-scoped finder.
 */
class GuestMappingTest extends AbstractPostgresContainerTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private GuestRepository guestRepository;

    private Project persistProject(String name) {
        User planner = em.persistAndFlush(new User(name + "-planner@wedding.test", "hash", "P", "L"));
        return em.persistAndFlush(new Project(name, planner));
    }

    @Test
    void persistsGuestUnderProject() {
        Project project = persistProject("Guest Wedding");
        Guest guest = new Guest("Alex & Jamie", RsvpStatus.ATTENDING, 2);
        guest.setEmail("alex@example.test");
        guest.setDietaryNotes("Vegetarian");
        project.addGuest(guest);

        em.persistAndFlush(project);
        em.clear();

        assertThat(guestRepository.findByProjectId(project.getId()))
                .singleElement()
                .satisfies(g -> {
                    assertThat(g.getName()).isEqualTo("Alex & Jamie");
                    assertThat(g.getRsvpStatus()).isEqualTo(RsvpStatus.ATTENDING);
                    assertThat(g.getPartySize()).isEqualTo(2);
                    assertThat(g.getDietaryNotes()).isEqualTo("Vegetarian");
                });
    }

    @Test
    void classificationEnumsAndRoleRoundTrip() {
        Project project = persistProject("Classified Wedding");
        // A fresh role (name/slug not in the V12 seed set, to avoid the unique-constraint clash).
        GuestRole role = em.persistAndFlush(new GuestRole("Test Role", "TEST_ROLE", 99));

        Guest guest = new Guest("Sam", RsvpStatus.ATTENDING, 1);
        guest.setPriority(GuestPriority.A);
        guest.setRelatedTo(RelatedTo.GROOM);
        guest.setRelationship(GuestRelationship.CLOSE_FRIEND);
        guest.setRole(role);
        project.addGuest(guest);

        em.persistAndFlush(project);
        em.clear();

        assertThat(guestRepository.findByProjectId(project.getId()))
                .singleElement()
                .satisfies(g -> {
                    assertThat(g.getPriority()).isEqualTo(GuestPriority.A);
                    assertThat(g.getRelatedTo()).isEqualTo(RelatedTo.GROOM);
                    assertThat(g.getRelationship()).isEqualTo(GuestRelationship.CLOSE_FRIEND);
                    assertThat(g.getRole().getSlug()).isEqualTo("TEST_ROLE");
                });

        // Enum columns store the literal name, like rsvp_status (char(1) reads back as a Character).
        Object storedPriority = em.getEntityManager()
                .createNativeQuery("SELECT priority FROM guests WHERE name = 'Sam'")
                .getSingleResult();
        assertThat(storedPriority).hasToString("A");
    }

    @Test
    void rsvpStatusIsStoredAsStringLiteral() {
        Project project = persistProject("RSVP Wedding");
        project.addGuest(new Guest("Pat", RsvpStatus.DECLINED, 1));
        em.persistAndFlush(project);

        Object stored = em.getEntityManager()
                .createNativeQuery("SELECT rsvp_status FROM guests WHERE name = 'Pat'")
                .getSingleResult();

        assertThat(stored).isEqualTo("DECLINED");
    }

    @Test
    void deletingProjectCascadesToGuests() {
        Project project = persistProject("Cascade Guest Wedding");
        project.addGuest(new Guest("Guest One", RsvpStatus.PENDING, 1));
        em.persistAndFlush(project);
        em.clear();

        Project reloaded = em.find(Project.class, project.getId());
        em.remove(reloaded);
        em.flush();
        em.clear();

        assertThat(guestRepository.findByProjectId(project.getId())).isEmpty();
    }

    @Test
    void finderScopesGuestsToTheirOwnProject() {
        Project projectA = persistProject("Guest Scope A");
        Project projectB = persistProject("Guest Scope B");
        projectA.addGuest(new Guest("A guest", RsvpStatus.ATTENDING, 1));
        projectB.addGuest(new Guest("B guest 1", RsvpStatus.PENDING, 1));
        projectB.addGuest(new Guest("B guest 2", RsvpStatus.MAYBE, 3));
        em.persistAndFlush(projectA);
        em.persistAndFlush(projectB);
        em.clear();

        assertThat(guestRepository.findByProjectId(projectA.getId())).hasSize(1);
        assertThat(guestRepository.findByProjectId(projectB.getId())).hasSize(2);
    }
}
