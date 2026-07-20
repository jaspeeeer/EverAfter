package com.wedding.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wedding.planner.AbstractPostgresContainerTest;
import com.wedding.planner.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Verifies the RBAC ownership rules encoded in {@link Project}:
 * a planner manages MANY projects, a couple owns exactly ONE.
 */
class ProjectOwnershipMappingTest extends AbstractPostgresContainerTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProjectRepository projectRepository;

    private User persistUser(String email) {
        return em.persistAndFlush(new User(email, "hash", "Test", "User"));
    }

    @Test
    void plannerManagesManyProjects() {
        User planner = persistUser("planner@wedding.test");
        em.persistAndFlush(new Project("Smith Wedding", planner));
        em.persistAndFlush(new Project("Jones Wedding", planner));
        em.clear();

        assertThat(projectRepository.findByPlannerId(planner.getId()))
                .hasSize(2)
                .extracting(Project::getName)
                .containsExactlyInAnyOrder("Smith Wedding", "Jones Wedding");
    }

    @Test
    void findByPlannerIdIsolatesProjectsPerPlanner() {
        User plannerA = persistUser("a@wedding.test");
        User plannerB = persistUser("b@wedding.test");
        em.persistAndFlush(new Project("A-1", plannerA));
        em.persistAndFlush(new Project("A-2", plannerA));
        em.persistAndFlush(new Project("B-1", plannerB));
        em.clear();

        assertThat(projectRepository.findByPlannerId(plannerB.getId()))
                .hasSize(1)
                .extracting(Project::getName)
                .containsExactly("B-1");
    }

    @Test
    void coupleOwnsExactlyOneProject() {
        User planner = persistUser("planner2@wedding.test");
        User couple = persistUser("couple@wedding.test");

        Project first = new Project("Couple Wedding", planner);
        first.setOwner(couple);
        em.persistAndFlush(first);
        em.clear();

        assertThat(projectRepository.findByOwnerId(couple.getId()))
                .isPresent()
                .get()
                .extracting(Project::getName)
                .isEqualTo("Couple Wedding");
    }

    @Test
    void rejectsSecondProjectForSameOwner() {
        User planner = persistUser("planner3@wedding.test");
        User couple = persistUser("couple2@wedding.test");

        Project first = new Project("First", planner);
        first.setOwner(couple);
        em.persistAndFlush(first);

        Project second = new Project("Second", planner);
        second.setOwner(couple);

        // The unique constraint on projects.owner_id enforces "one project per couple".
        // saveAndFlush routes through the repository proxy for Spring exception translation.
        assertThatThrownBy(() -> projectRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plannerIsRequired() {
        Project orphan = new Project("No Planner", null);

        assertThatThrownBy(() -> em.persistAndFlush(orphan))
                .isInstanceOf(Exception.class);
    }
}
