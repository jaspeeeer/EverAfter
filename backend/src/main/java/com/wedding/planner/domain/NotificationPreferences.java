package com.wedding.planner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-user in-app notification channel toggles. A missing row is treated as all-true; the
 * service auto-creates a defaults row on first read.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferences {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "fk_notification_preferences_user")
    )
    private User user;

    @Column(name = "inapp_task_due", nullable = false)
    private boolean inappTaskDue = true;

    @Column(name = "inapp_payment_due", nullable = false)
    private boolean inappPaymentDue = true;

    @Column(name = "inapp_countdown", nullable = false)
    private boolean inappCountdown = true;

    protected NotificationPreferences() {
        // Required by JPA.
    }

    public NotificationPreferences(User user) {
        this.user = user;
    }

    public UUID getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public boolean isInappTaskDue() {
        return inappTaskDue;
    }

    public void setInappTaskDue(boolean inappTaskDue) {
        this.inappTaskDue = inappTaskDue;
    }

    public boolean isInappPaymentDue() {
        return inappPaymentDue;
    }

    public void setInappPaymentDue(boolean inappPaymentDue) {
        this.inappPaymentDue = inappPaymentDue;
    }

    public boolean isInappCountdown() {
        return inappCountdown;
    }

    public void setInappCountdown(boolean inappCountdown) {
        this.inappCountdown = inappCountdown;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotificationPreferences other)) {
            return false;
        }
        return userId != null && userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
