package com.wedding.planner.web;

import com.wedding.planner.notification.ReminderScheduler;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only endpoints for local testing. Only active under the {@code dev} Spring profile so this
 * cannot be reached in production even if the URL is guessed.
 */
@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevController {

    private final ReminderScheduler reminderScheduler;

    public DevController(ReminderScheduler reminderScheduler) {
        this.reminderScheduler = reminderScheduler;
    }

    /** Runs the reminder sweep synchronously. Idempotent thanks to dedupe keys. */
    @PostMapping("/run-reminders")
    public Map<String, String> runReminders() {
        reminderScheduler.run();
        return Map.of("status", "ok");
    }
}
