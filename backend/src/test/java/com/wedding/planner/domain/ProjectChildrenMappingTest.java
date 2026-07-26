package com.wedding.planner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wedding.planner.AbstractPostgresContainerTest;
import com.wedding.planner.repository.ExpenseRepository;
import com.wedding.planner.repository.TaskRepository;
import com.wedding.planner.repository.VendorCategoryRepository;
import com.wedding.planner.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Verifies Project -> Task/Vendor/Expense mappings: enum round-tripping, cascade + orphan
 * removal, and project-scoped finder methods.
 */
class ProjectChildrenMappingTest extends AbstractPostgresContainerTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private VendorCategoryRepository vendorCategoryRepository;

    /** Vendor categories are seeded by the V6 migration; look one up by slug. */
    private VendorCategory category(String slug) {
        return vendorCategoryRepository.findBySlug(slug).orElseThrow();
    }

    private Project persistProject(String name) {
        User planner = em.persistAndFlush(new User(name + "-planner@wedding.test", "hash", "P", "L"));
        return em.persistAndFlush(new Project(name, planner));
    }

    @Test
    void persistsTasksVendorsAndExpensesUnderProject() {
        Project project = persistProject("Full Wedding");

        Task task = new Task("Book venue", TaskStatus.IN_PROGRESS);
        task.setDueDate(LocalDate.of(2026, 9, 1));
        project.addTask(task);

        Vendor vendor = new Vendor("Blooms & Co", category("FLORIST"));
        vendor.setContactEmail("hello@blooms.test");
        vendor.setBooked(true);
        project.addVendor(vendor);

        Expense expense = new Expense("Deposit", new BigDecimal("1500.00"), category("VENUE"));
        expense.setPaid(true);
        project.addExpense(expense);

        em.persistAndFlush(project);
        em.clear();

        assertThat(taskRepository.findByProjectId(project.getId()))
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getTitle()).isEqualTo("Book venue");
                    assertThat(t.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
                });
        assertThat(vendorRepository.findByProjectId(project.getId()))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.getCategory().getSlug()).isEqualTo("FLORIST");
                    assertThat(v.isBooked()).isTrue();
                });
        assertThat(expenseRepository.findByProjectId(project.getId()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getAmount()).isEqualByComparingTo("1500.00");
                    assertThat(e.getCategory().getSlug()).isEqualTo("VENUE");
                });
    }

    @Test
    void enumColumnsAreStoredAsStringLiterals() {
        Project project = persistProject("Enum Wedding");
        project.addTask(new Task("Cake tasting", TaskStatus.DONE));
        em.persistAndFlush(project);

        Object storedStatus = em.getEntityManager()
                .createNativeQuery("SELECT status FROM tasks WHERE title = 'Cake tasting'")
                .getSingleResult();

        assertThat(storedStatus).isEqualTo("DONE");
    }

    @Test
    void deletingProjectCascadesToChildren() {
        Project project = persistProject("Cascade Wedding");
        project.addTask(new Task("A task", TaskStatus.TODO));
        project.addVendor(new Vendor("A vendor", category("MUSIC")));
        project.addExpense(new Expense("A cost", new BigDecimal("42.00"), category("MUSIC")));
        em.persistAndFlush(project);
        em.clear();

        Project reloaded = em.find(Project.class, project.getId());
        em.remove(reloaded);
        em.flush();
        em.clear();

        assertThat(taskRepository.findByProjectId(project.getId())).isEmpty();
        assertThat(vendorRepository.findByProjectId(project.getId())).isEmpty();
        assertThat(expenseRepository.findByProjectId(project.getId())).isEmpty();
    }

    @Test
    void orphanRemovalDeletesDetachedTask() {
        Project project = persistProject("Orphan Wedding");
        Task keep = new Task("Keep", TaskStatus.TODO);
        Task drop = new Task("Drop", TaskStatus.TODO);
        project.addTask(keep);
        project.addTask(drop);
        em.persistAndFlush(project);

        project.removeTask(drop);
        em.persistAndFlush(project);
        em.clear();

        assertThat(taskRepository.findByProjectId(project.getId()))
                .extracting(Task::getTitle)
                .containsExactly("Keep");
    }

    @Test
    void finderScopesChildrenToTheirOwnProject() {
        Project projectA = persistProject("Scope A");
        Project projectB = persistProject("Scope B");
        projectA.addTask(new Task("A task", TaskStatus.TODO));
        projectB.addTask(new Task("B task 1", TaskStatus.TODO));
        projectB.addTask(new Task("B task 2", TaskStatus.TODO));
        em.persistAndFlush(projectA);
        em.persistAndFlush(projectB);
        em.clear();

        assertThat(taskRepository.findByProjectId(projectA.getId())).hasSize(1);
        assertThat(taskRepository.findByProjectId(projectB.getId())).hasSize(2);
    }
}
