-- ---------------------------------------------------------------------
-- V19 :: Invitation-page metadata on projects.
--
-- The public RSVP page at /rsvp/{token} has only ever shown the project
-- name and wedding date. This migration adds the fields every downstream
-- invitation-page enhancement needs to render, in one shot:
--
--   * venue_name / venue_address       -- where the wedding happens
--   * ceremony_time / reception_time   -- when the day's events start
--
-- Batched into the same migration to avoid churning V19/V20/V21 for one
-- coherent story:
--
--   * allow_guest_party_size + max_party_size
--       Phase 2 (party-size toggle) — the planner/admin/couple opt-in
--       flag that lets guests set their own party size on the RSVP form
--       (default false preserves the current server-authoritative
--       "reset to 1" behavior).
--
--   * cover_attachment_id
--       Phase 3 (project cover photo) — a strict FK to attachments(id)
--       for singularity; ON DELETE SET NULL so removing the underlying
--       attachment row doesn't fail the FK check on the project side
--       (the delete endpoint clears this column first, this is defense
--       in depth against orphaning).
--
-- Nothing sets these columns yet — Java entity/DTO/service wiring lands
-- in the same phase 1 commit, but phase 2 and 3 will fill in the rest.
-- ---------------------------------------------------------------------

ALTER TABLE projects ADD COLUMN venue_name             varchar(200);
ALTER TABLE projects ADD COLUMN venue_address          varchar(500);
ALTER TABLE projects ADD COLUMN ceremony_time          time;
ALTER TABLE projects ADD COLUMN reception_time         time;
ALTER TABLE projects ADD COLUMN allow_guest_party_size boolean NOT NULL DEFAULT false;
ALTER TABLE projects ADD COLUMN max_party_size         integer;
ALTER TABLE projects ADD COLUMN cover_attachment_id    uuid;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_cover_attachment
    FOREIGN KEY (cover_attachment_id) REFERENCES attachments (id) ON DELETE SET NULL;
