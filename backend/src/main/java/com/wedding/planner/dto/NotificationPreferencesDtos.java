package com.wedding.planner.dto;

import com.wedding.planner.domain.NotificationPreferences;

public final class NotificationPreferencesDtos {

    private NotificationPreferencesDtos() {}

    public record NotificationPreferencesResponse(
            boolean inappTaskDue,
            boolean inappPaymentDue,
            boolean inappCountdown) {

        public static NotificationPreferencesResponse from(NotificationPreferences prefs) {
            return new NotificationPreferencesResponse(
                    prefs.isInappTaskDue(),
                    prefs.isInappPaymentDue(),
                    prefs.isInappCountdown());
        }
    }

    public record NotificationPreferencesRequest(
            boolean inappTaskDue,
            boolean inappPaymentDue,
            boolean inappCountdown) {}
}
