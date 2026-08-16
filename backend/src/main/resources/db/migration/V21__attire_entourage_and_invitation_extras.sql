-- ---------------------------------------------------------------------
-- V21 :: Attire guidance, an entourage (wedding party) list, and a few
-- small invitation-etiquette fields (RSVP deadline, kids policy, a
-- social hashtag) — all optional, all nullable, all rendered on the
-- public invitation page only when set (same pattern as V19/V20).
--
-- The attire color palette is stored as a single comma-separated hex
-- list (e.g. "#f4a5a5,#a5c4f4") rather than a child table: like venue
-- name/address, it has no independent lifecycle and no per-guest
-- variation. Entourage is different — it's a genuine ordered list of
-- named rows — so it gets its own table, following the same shape as
-- guests (own table, project_id FK, ON DELETE CASCADE) rather than the
-- admin-managed lookup tables (guest_roles/vendor_categories), which
-- are global, not project-scoped.
-- ---------------------------------------------------------------------

ALTER TABLE projects ADD COLUMN dress_code         varchar(200);
ALTER TABLE projects ADD COLUMN attire_notes_men    varchar(500);
ALTER TABLE projects ADD COLUMN attire_notes_women  varchar(500);
ALTER TABLE projects ADD COLUMN attire_palette      varchar(300);
ALTER TABLE projects ADD COLUMN rsvp_deadline       date;
ALTER TABLE projects ADD COLUMN kids_policy         varchar(300);
ALTER TABLE projects ADD COLUMN social_hashtag      varchar(100);

CREATE TABLE entourage_members (
    id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    project_id  uuid         NOT NULL,
    role        varchar(100) NOT NULL,
    name        varchar(200) NOT NULL,
    sort_order  integer      NOT NULL DEFAULT 0,
    CONSTRAINT pk_entourage_members PRIMARY KEY (id),
    CONSTRAINT fk_entourage_members_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX ix_entourage_members_project ON entourage_members (project_id);
