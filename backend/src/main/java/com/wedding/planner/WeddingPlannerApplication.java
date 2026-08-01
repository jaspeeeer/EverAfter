package com.wedding.planner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Wedding Planner backend.
 *
 * <p>{@code @EnableScheduling} powers the daily notification reminders (see
 * {@code notification.ReminderScheduler}).
 */
@SpringBootApplication
@EnableScheduling
public class WeddingPlannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeddingPlannerApplication.class, args);
    }
}
