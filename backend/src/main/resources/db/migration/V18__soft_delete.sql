-- ---------------------------------------------------------------------
-- V18 :: Soft delete for vendors, guests, expenses, and tasks.
--
-- Adds a nullable deleted_at timestamp to each table; the entities gain
-- @SQLRestriction("deleted_at is null") so every existing read (derived
-- finders, JPQL, and JpaRepository.count()) transparently excludes
-- tombstoned rows with no per-query changes. Nothing sets the column
-- yet — deletes stay hard through this migration; a later change wires
-- the four services' delete() methods to stamp it instead.
--
-- The five FKs from tasks/vendors/expenses/guests/timeline_events back
-- to projects have never had an ON DELETE clause — project deletion
-- has only ever worked because Hibernate's CascadeType.ALL on Project's
-- five child collections issues explicit DELETEs for each row before
-- the parent delete. @SQLRestriction hides tombstoned rows from that
-- same collection traversal, so without a DB-level cascade a project
-- with any soft-deleted child would fail to delete on an FK violation.
-- Converting these five FKs to ON DELETE CASCADE makes project purge
-- correct regardless of soft-delete state — the JPA cascade keeps
-- deleting live rows exactly as before, and the DB now sweeps whatever
-- JPA can no longer see. timeline_events has no deleted_at column (out
-- of scope for this feature) but gets the same FK fix for the same
-- reason: it's cascaded via the identical Project mapping.
-- ---------------------------------------------------------------------

ALTER TABLE vendors  ADD COLUMN deleted_at timestamp;
ALTER TABLE guests   ADD COLUMN deleted_at timestamp;
ALTER TABLE expenses ADD COLUMN deleted_at timestamp;
ALTER TABLE tasks    ADD COLUMN deleted_at timestamp;

CREATE INDEX ix_vendors_project_live  ON vendors  (project_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_guests_project_live   ON guests   (project_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_expenses_project_live ON expenses (project_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_tasks_project_live    ON tasks    (project_id) WHERE deleted_at IS NULL;

ALTER TABLE tasks           DROP CONSTRAINT fk_tasks_project;
ALTER TABLE tasks           ADD  CONSTRAINT fk_tasks_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE vendors         DROP CONSTRAINT fk_vendors_project;
ALTER TABLE vendors         ADD  CONSTRAINT fk_vendors_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE expenses        DROP CONSTRAINT fk_expenses_project;
ALTER TABLE expenses        ADD  CONSTRAINT fk_expenses_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE guests          DROP CONSTRAINT fk_guests_project;
ALTER TABLE guests          ADD  CONSTRAINT fk_guests_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

ALTER TABLE timeline_events DROP CONSTRAINT fk_timeline_events_project;
ALTER TABLE timeline_events ADD  CONSTRAINT fk_timeline_events_project
    FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;
