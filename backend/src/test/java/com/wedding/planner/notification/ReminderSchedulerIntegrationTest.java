package com.wedding.planner.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.wedding.planner.AbstractIntegrationTest;
import com.wedding.planner.domain.Notification;
import com.wedding.planner.domain.NotificationPreferences;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Role;
import com.wedding.planner.domain.RoleName;
import com.wedding.planner.domain.Task;
import com.wedding.planner.domain.TaskStatus;
import com.wedding.planner.domain.User;
import com.wedding.planner.repository.NotificationPreferencesRepository;
import com.wedding.planner.repository.NotificationRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.RoleRepository;
import com.wedding.planner.repository.TaskRepository;
import com.wedding.planner.repository.UserRepository;
import com.wedding.planner.repository.VendorCategoryRepository;
import com.wedding.planner.repository.VendorPaymentRepository;
import com.wedding.planner.repository.VendorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exercises the scheduler end-to-end against a real Postgres container. Because
 * {@link NotificationService#enqueue} runs in REQUIRES_NEW, fixture data must be committed
 * before the scheduler runs — that's why every setup/read block goes through
 * {@link TransactionTemplate}. Unique per-test suffixes keep the tests isolated without a
 * class-level {@code @Transactional} rollback.
 */
class ReminderSchedulerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ReminderScheduler scheduler;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private ProjectRepository projects;
    @Autowired private TaskRepository tasks;
    @Autowired private VendorRepository vendors;
    @Autowired private VendorCategoryRepository vendorCategories;
    @Autowired private VendorPaymentRepository vendorPayments;
    @Autowired private NotificationRepository notifications;
    @Autowired private NotificationPreferencesRepository preferences;
    @Autowired private TransactionTemplate tx;

    private final List<Fixture> createdFixtures = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // Because AbstractIntegrationTest shares one Postgres container across the whole JVM,
        // rows we create leak into other test classes' assertions (notably vendor reports). Delete
        // each seeded project (cascades through the JPA @OneToMany's to tasks/vendors/payments/etc)
        // and its users so other suites see a clean slate.
        for (Fixture f : createdFixtures) {
            tx.executeWithoutResult(status -> {
                projects.findById(f.projectId).ifPresent(projects::delete);
                users.findById(f.plannerId).ifPresent(users::delete);
                users.findById(f.ownerId).ifPresent(users::delete);
            });
        }
        createdFixtures.clear();
    }

    @Test
    void writesOneNotificationPerRecipientAndIsIdempotent() {
        Fixture f = seed("sched-basic");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        tx.executeWithoutResult(status -> {
            Project p = projects.findById(f.projectId).orElseThrow();
            Task t = new Task("Book florist", TaskStatus.TODO);
            t.setProject(p);
            t.setDueDate(today.plusDays(3));
            tasks.saveAndFlush(t);
        });

        scheduler.run();

        List<Notification> plannerFirst = readAll(f.plannerId);
        assertThat(plannerFirst).hasSize(1);
        assertThat(plannerFirst.get(0).getTitle()).contains("Book florist");
        assertThat(plannerFirst.get(0).getDedupeKey()).contains("task:").contains(":3d:");
        assertThat(readAll(f.ownerId)).hasSize(1);

        // Same-day rerun — partial-unique index collapses duplicates.
        scheduler.run();
        assertThat(readAll(f.plannerId)).hasSize(1);
        assertThat(readAll(f.ownerId)).hasSize(1);
    }

    @Test
    void respectsChannelPreference() {
        Fixture f = seed("sched-prefs");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // Disable the task-due channel for the couple only.
        tx.executeWithoutResult(status -> {
            User owner = users.findById(f.ownerId).orElseThrow();
            NotificationPreferences prefs = new NotificationPreferences(owner);
            prefs.setInappTaskDue(false);
            preferences.saveAndFlush(prefs);
        });

        tx.executeWithoutResult(status -> {
            Project p = projects.findById(f.projectId).orElseThrow();
            Task t = new Task("Confirm venue", TaskStatus.TODO);
            t.setProject(p);
            t.setDueDate(today.plusDays(1));
            tasks.saveAndFlush(t);
        });

        scheduler.run();

        assertThat(readAll(f.plannerId)).hasSize(1);
        assertThat(readAll(f.ownerId)).isEmpty();
    }

    @Test
    void plannedPaymentDueSoonFiresPlannerOnly() {
        Fixture f = seed("sched-payment");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        tx.executeWithoutResult(status -> {
            Project p = projects.findById(f.projectId).orElseThrow();
            com.wedding.planner.domain.VendorCategory cat =
                    vendorCategories.findByActiveTrueOrderBySortOrderAsc().stream()
                            .findFirst().orElseThrow();
            com.wedding.planner.domain.Vendor v = new com.wedding.planner.domain.Vendor("Florist", cat);
            v.setProject(p);
            v.setAgreedPrice(new BigDecimal("10000.00"));
            v = vendors.saveAndFlush(v);
            // A planned installment due in exactly 7 days.
            com.wedding.planner.domain.VendorPayment vp = com.wedding.planner.domain.VendorPayment.planned(
                    v, new BigDecimal("3000.00"), today.plusDays(7), "Down payment");
            vendorPayments.saveAndFlush(vp);
        });

        scheduler.run();

        List<Notification> plannerRows = readAll(f.plannerId);
        assertThat(plannerRows).hasSize(1);
        assertThat(plannerRows.get(0).getTitle()).contains("Payment due in 7 days");
        // Couple gets nothing — payment reminders are planner-only.
        assertThat(readAll(f.ownerId)).isEmpty();
    }

    @Test
    void softDeletedTaskGeneratesNoReminder() {
        Fixture f = seed("sched-soft-task");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        tx.executeWithoutResult(status -> {
            Project p = projects.findById(f.projectId).orElseThrow();
            Task t = new Task("Soft-deleted task", TaskStatus.TODO);
            t.setProject(p);
            t.setDueDate(today.plusDays(3));
            t.setDeletedAt(Instant.now());
            tasks.saveAndFlush(t);
        });

        scheduler.run();

        assertThat(readAll(f.plannerId)).isEmpty();
        assertThat(readAll(f.ownerId)).isEmpty();
    }

    @Test
    void softDeletedVendorsPlannedPaymentGeneratesNoReminder() {
        Fixture f = seed("sched-soft-payment");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        tx.executeWithoutResult(status -> {
            Project p = projects.findById(f.projectId).orElseThrow();
            com.wedding.planner.domain.VendorCategory cat =
                    vendorCategories.findByActiveTrueOrderBySortOrderAsc().stream()
                            .findFirst().orElseThrow();
            com.wedding.planner.domain.Vendor v = new com.wedding.planner.domain.Vendor("Soft Deleted Florist", cat);
            v.setProject(p);
            v.setAgreedPrice(new BigDecimal("10000.00"));
            v.setDeletedAt(Instant.now());
            v = vendors.saveAndFlush(v);
            com.wedding.planner.domain.VendorPayment vp = com.wedding.planner.domain.VendorPayment.planned(
                    v, new BigDecimal("3000.00"), today.plusDays(7), "Down payment");
            vendorPayments.saveAndFlush(vp);
        });

        scheduler.run();

        assertThat(readAll(f.plannerId)).isEmpty();
        assertThat(readAll(f.ownerId)).isEmpty();
    }

    @Test
    void countdownFiresOnExactHorizon() {
        Fixture f = seed("sched-countdown");

        tx.executeWithoutResult(status -> {
            Project p = projects.findById(f.projectId).orElseThrow();
            p.setWeddingDate(LocalDate.now(ZoneOffset.UTC).plusDays(7));
            projects.saveAndFlush(p);
        });

        scheduler.run();

        List<Notification> planner = readAll(f.plannerId);
        assertThat(planner).hasSize(1);
        assertThat(planner.get(0).getTitle()).contains("7 days until");
    }

    // ---------------------------------------------------------------

    private record Fixture(UUID plannerId, UUID ownerId, UUID projectId) {}

    private Fixture seed(String prefix) {
        Fixture f = tx.execute(status -> {
            Role plannerRole = roles.findByName(RoleName.ROLE_PLANNER).orElseThrow();
            Role userRole = roles.findByName(RoleName.ROLE_USER).orElseThrow();
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User planner = new User(prefix + "-planner-" + suffix + "@test", "$2a$10$x", "P", "L");
            planner.addRole(plannerRole);
            planner = users.saveAndFlush(planner);
            User owner = new User(prefix + "-owner-" + suffix + "@test", "$2a$10$x", "O", "W");
            owner.addRole(userRole);
            owner = users.saveAndFlush(owner);
            Project p = new Project(prefix + " Wedding", planner);
            p.setOwner(owner);
            p = projects.saveAndFlush(p);
            return new Fixture(planner.getId(), owner.getId(), p.getId());
        });
        createdFixtures.add(f);
        return f;
    }

    private List<Notification> readAll(UUID userId) {
        return tx.execute(status ->
                notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 20)));
    }
}
