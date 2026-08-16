-- ---------------------------------------------------------------------
-- V20 :: Split the single "venue" into ceremony (church) and reception
-- (venue) locations, each with its own name/address, and add photo
-- slots for both.
--
-- Real wedding invitations almost always have two distinct places —
-- ceremony_time/reception_time already correctly modeled two events,
-- but venue_name/venue_address only ever described one place for both.
--
-- Backfill assumption (documented, not measured, same spirit as V17's
-- own backfill note): the single legacy venue is copied into the
-- ceremony_venue_* columns, on the theory that ceremony_time is what it
-- was originally anchored to. There is no way to recover which rows
-- actually meant "reception" instead — planners can correct this
-- per-project after the migration, same as V17's guest-name split.
--
-- ceremony_photo_attachment_id / reception_photo_attachment_id follow
-- the exact precedent of V19's cover_attachment_id: nullable FK to
-- attachments(id), ON DELETE SET NULL. A project can have at most one
-- photo per slot (cover, ceremony, reception) — three independent
-- slots, not a gallery; AttachmentOwnerType.PROJECT is reused for all
-- three since the FK column is what distinguishes them, not the owner
-- type (see AttachmentService/ProjectService).
-- ---------------------------------------------------------------------

ALTER TABLE projects ADD COLUMN ceremony_venue_name             varchar(200);
ALTER TABLE projects ADD COLUMN ceremony_venue_address          varchar(500);
ALTER TABLE projects ADD COLUMN reception_venue_name            varchar(200);
ALTER TABLE projects ADD COLUMN reception_venue_address         varchar(500);
ALTER TABLE projects ADD COLUMN ceremony_photo_attachment_id    uuid;
ALTER TABLE projects ADD COLUMN reception_photo_attachment_id   uuid;

UPDATE projects SET ceremony_venue_name = venue_name, ceremony_venue_address = venue_address
    WHERE venue_name IS NOT NULL OR venue_address IS NOT NULL;

ALTER TABLE projects DROP COLUMN venue_name;
ALTER TABLE projects DROP COLUMN venue_address;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_ceremony_photo_attachment
    FOREIGN KEY (ceremony_photo_attachment_id) REFERENCES attachments (id) ON DELETE SET NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_reception_photo_attachment
    FOREIGN KEY (reception_photo_attachment_id) REFERENCES attachments (id) ON DELETE SET NULL;
