-- ---------------------------------------------------------------------
-- V15 :: Per-project activity log (audit trail).
--
-- One row per create/update/delete on a project-scoped entity (tasks,
-- vendors, vendor_payments, expenses, guests, invitations,
-- timeline_events, and the project itself). Renders as the "Activity"
-- tab on the project detail page. The actor is denormalised to
-- `actor_email` so log lines remain readable even if the user is
-- deleted; `actor_user_id` FK is ON DELETE SET NULL for the same
-- reason.
--
-- `metadata jsonb` is populated for future filtering / diff UIs; v1
-- only renders `summary`.
-- ---------------------------------------------------------------------

CREATE TABLE activity_log (
    id             uuid         NOT NULL,
    project_id     uuid         NOT NULL,
    actor_user_id  uuid,
    actor_email    varchar(255),
    entity_type    varchar(40)  NOT NULL,
    entity_id      uuid,
    action         varchar(16)  NOT NULL,
    summary        varchar(500) NOT NULL,
    metadata       jsonb,
    created_at     timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_activity_log PRIMARY KEY (id),
    CONSTRAINT fk_activity_log_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_activity_log_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_activity_log_project_created
    ON activity_log (project_id, created_at DESC);

CREATE INDEX ix_activity_log_actor
    ON activity_log (actor_user_id);

CREATE INDEX ix_activity_log_entity
    ON activity_log (entity_type, entity_id);
