package com.wedding.planner.notification;

import com.wedding.planner.domain.Notification;
import com.wedding.planner.domain.NotificationPreferences;
import com.wedding.planner.domain.NotificationType;
import com.wedding.planner.domain.Project;
import com.wedding.planner.domain.Task;
import com.wedding.planner.domain.TaskStatus;
import com.wedding.planner.domain.User;
import com.wedding.planner.domain.Vendor;
import com.wedding.planner.domain.VendorPayment;
import com.wedding.planner.repository.NotificationPreferencesRepository;
import com.wedding.planner.repository.ProjectRepository;
import com.wedding.planner.repository.TaskRepository;
import com.wedding.planner.repository.VendorPaymentRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily sweep that materialises in-app reminder rows for tasks due soon and wedding-date
 * countdown milestones. Vendor payment reminders arrive in Step 3.
 *
 * <p>Idempotency is enforced by the partial-unique index {@code uq_notifications_user_dedupe}:
 * every call to {@code NotificationService.enqueue} passes a stable {@code dedupe_key} keyed to
 * the source entity and the horizon (e.g. {@code task:<uuid>:7d}); a same-day rerun becomes a
 * no-op. That makes both the {@code @Scheduled} tick and the dev-only manual trigger safe to
 * run repeatedly.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private static final Set<Integer> TASK_HORIZONS = Set.of(7, 3, 1);
    private static final Set<Integer> PAYMENT_HORIZONS = Set.of(14, 7, 1);
    private static final Set<Integer> COUNTDOWN_HORIZONS = Set.of(90, 30, 7, 1);

    private final TaskRepository taskRepository;
    private final VendorPaymentRepository vendorPaymentRepository;
    private final ProjectRepository projectRepository;
    private final NotificationService notificationService;
    private final NotificationPreferencesRepository preferencesRepository;
    private final boolean enabled;

    public ReminderScheduler(TaskRepository taskRepository,
                             VendorPaymentRepository vendorPaymentRepository,
                             ProjectRepository projectRepository,
                             NotificationService notificationService,
                             NotificationPreferencesRepository preferencesRepository,
                             @Value("${app.notifications.enabled:true}") boolean enabled) {
        this.taskRepository = taskRepository;
        this.vendorPaymentRepository = vendorPaymentRepository;
        this.projectRepository = projectRepository;
        this.notificationService = notificationService;
        this.preferencesRepository = preferencesRepository;
        this.enabled = enabled;
    }

    /**
     * Runs once daily at {@code app.notifications.run-hour-utc}. Default 01:00 UTC (~09:00 PHT).
     * Read-only outer transaction — each recipient row is persisted by {@link NotificationService}
     * in its own REQUIRES_NEW transaction so a per-user error can't derail the batch.
     */
    @Scheduled(cron = "0 0 ${app.notifications.run-hour-utc:1} * * *", zone = "UTC")
    @Transactional(readOnly = true)
    public void run() {
        if (!enabled) {
            log.debug("Notifications disabled; skipping scheduler tick.");
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        log.info("Running reminder sweep for {}", today);
        int taskWrites = runTaskReminders(today);
        int paymentWrites = runPaymentReminders(today);
        int countdownWrites = runCountdownReminders(today);
        log.info("Reminder sweep complete: {} task, {} payment, {} countdown notifications written",
                taskWrites, paymentWrites, countdownWrites);
    }

    // ------------------------------------------------------------------
    // Task due-soon reminders
    // ------------------------------------------------------------------

    private int runTaskReminders(LocalDate today) {
        Map<LocalDate, Integer> horizonDates = new java.util.HashMap<>();
        for (Integer h : TASK_HORIZONS) horizonDates.put(today.plusDays(h), h);
        List<Task> due = taskRepository.findByDueDateInAndStatusNot(horizonDates.keySet(), TaskStatus.DONE);
        int writes = 0;
        for (Task task : due) {
            int horizon = horizonDates.get(task.getDueDate());
            for (User recipient : recipientsForProject(task.getProject())) {
                if (!enabledFor(recipient, NotificationPreferences::isInappTaskDue)) continue;
                String dedupe = "task:" + task.getId() + ":" + horizon + "d:" + today;
                String title = horizon == 1
                        ? "Task due tomorrow: " + task.getTitle()
                        : "Task due in " + horizon + " days: " + task.getTitle();
                String body = "Due " + task.getDueDate();
                String linkPath = "/projects/" + task.getProject().getId() + "/checklist";
                if (notificationService.enqueue(
                        recipient,
                        NotificationType.TASK_DUE_SOON,
                        title,
                        body,
                        linkPath,
                        "TASK",
                        task.getId(),
                        task.getProject(),
                        dedupe)) {
                    writes++;
                }
            }
        }
        return writes;
    }

    // ------------------------------------------------------------------
    // Vendor payment due-soon reminders (planner only)
    // ------------------------------------------------------------------

    private int runPaymentReminders(LocalDate today) {
        Map<LocalDate, Integer> horizonDates = new java.util.HashMap<>();
        for (Integer h : PAYMENT_HORIZONS) horizonDates.put(today.plusDays(h), h);
        List<VendorPayment> planned = vendorPaymentRepository.findPlannedByDueDateIn(horizonDates.keySet());
        int writes = 0;
        for (VendorPayment payment : planned) {
            Vendor vendor = payment.getVendor();
            Project project = vendor.getProject();
            int horizon = horizonDates.get(payment.getDueDate());
            // Planner only — the couple isn't expected to chase payments.
            User planner = project.getPlanner();
            if (planner == null) continue;
            if (!enabledFor(planner, NotificationPreferences::isInappPaymentDue)) continue;
            String dedupe = "payment:" + payment.getId() + ":" + horizon + "d";
            String title = horizon == 1
                    ? "Payment due tomorrow: " + vendor.getName()
                    : "Payment due in " + horizon + " days: " + vendor.getName();
            String body = "Amount " + payment.getAmount() + " · due " + payment.getDueDate();
            String linkPath = "/projects/" + project.getId() + "/vendors";
            if (notificationService.enqueue(
                    planner,
                    NotificationType.PAYMENT_DUE_SOON,
                    title,
                    body,
                    linkPath,
                    "VENDOR_PAYMENT",
                    payment.getId(),
                    project,
                    dedupe)) {
                writes++;
            }
        }
        return writes;
    }

    // ------------------------------------------------------------------
    // Wedding-date countdown reminders
    // ------------------------------------------------------------------

    private int runCountdownReminders(LocalDate today) {
        Map<LocalDate, Integer> horizonDates = new java.util.HashMap<>();
        for (Integer h : COUNTDOWN_HORIZONS) horizonDates.put(today.plusDays(h), h);
        List<Project> projects = projectRepository.findByWeddingDateIn(horizonDates.keySet());
        int writes = 0;
        for (Project project : projects) {
            int horizon = horizonDates.get(project.getWeddingDate());
            for (User recipient : recipientsForProject(project)) {
                if (!enabledFor(recipient, NotificationPreferences::isInappCountdown)) continue;
                String dedupe = "countdown:" + project.getId() + ":" + horizon + "d";
                String title = horizon == 1
                        ? "Tomorrow is the big day!"
                        : horizon + " days until " + project.getName();
                String body = horizon == 1
                        ? "The wedding is tomorrow (" + project.getWeddingDate() + ")."
                        : "The wedding is on " + project.getWeddingDate() + ".";
                String linkPath = "/projects/" + project.getId();
                if (notificationService.enqueue(
                        recipient,
                        NotificationType.WEDDING_COUNTDOWN,
                        title,
                        body,
                        linkPath,
                        "PROJECT",
                        project.getId(),
                        project,
                        dedupe)) {
                    writes++;
                }
            }
        }
        return writes;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Planner + couple (if the couple has accepted). Duplicates collapsed. */
    private List<User> recipientsForProject(Project project) {
        List<User> out = new ArrayList<>(2);
        User planner = project.getPlanner();
        if (planner != null) out.add(planner);
        User owner = project.getOwner();
        if (owner != null && (planner == null || !owner.getId().equals(planner.getId()))) {
            out.add(owner);
        }
        return out;
    }

    /**
     * True if the given channel is enabled for this user. A missing preferences row is treated
     * as all-true (no write happens here — the scheduler runs read-only; the row is materialised
     * lazily by NotificationService the first time the user visits the settings page).
     */
    private boolean enabledFor(User user, Predicate<NotificationPreferences> channel) {
        UUID id = user.getId();
        return preferencesRepository.findById(id)
                .map(channel::test)
                .orElse(true);
    }
}
