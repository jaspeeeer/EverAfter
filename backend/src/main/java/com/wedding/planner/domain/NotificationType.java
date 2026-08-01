package com.wedding.planner.domain;

/**
 * Kind of notification. Persisted as a string in {@code notifications.type} so new types can be
 * added without a migration.
 */
public enum NotificationType {
    TASK_DUE_SOON,
    PAYMENT_DUE_SOON,
    WEDDING_COUNTDOWN
}
