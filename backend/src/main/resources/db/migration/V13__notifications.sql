-- ---------------------------------------------------------------------
-- V13 :: Notifications feed + per-user in-app preferences.
--
-- The `notifications` table is the persisted feed powering the top-nav
-- bell. A daily scheduler (ReminderScheduler) materialises reminder rows
-- for task due dates, vendor payment due dates, and wedding countdown
-- milestones. The partial-unique index on (user_id, dedupe_key) enforces
-- idempotency directly on the feed — the scheduler uses
-- INSERT ... ON CONFLICT DO NOTHING, so a same-day rerun is a no-op.
--
-- Email dispatch is intentionally out of scope for MVP; the columns are
-- channel-agnostic so an EmailSender can drop in later without a
-- migration.
-- ---------------------------------------------------------------------

CREATE TABLE notifications (
    id           uuid         NOT NULL,
    user_id      uuid         NOT NULL,
    project_id   uuid,
    type         varchar(64)  NOT NULL,
    title        varchar(200) NOT NULL,
    body         text         NOT NULL,
    link_path    varchar(500),
    entity_type  varchar(40),
    entity_id    uuid,
    dedupe_key   varchar(200),
    read_at      timestamp,
    created_at   timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX ix_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX ix_notifications_user_unread
    ON notifications (user_id)
    WHERE read_at IS NULL;

-- Idempotency for the scheduler: same user + same dedupe key can exist
-- only once. Partial so ad-hoc notifications without a key are unconstrained.
CREATE UNIQUE INDEX uq_notifications_user_dedupe
    ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

-- ---------------------------------------------------------------------
-- Per-user in-app channel toggles. A missing row is treated as all-true
-- by NotificationService.getOrCreatePreferences.
-- ---------------------------------------------------------------------
CREATE TABLE notification_preferences (
    user_id             uuid    NOT NULL,
    inapp_task_due      boolean NOT NULL DEFAULT true,
    inapp_payment_due   boolean NOT NULL DEFAULT true,
    inapp_countdown     boolean NOT NULL DEFAULT true,
    CONSTRAINT pk_notification_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
