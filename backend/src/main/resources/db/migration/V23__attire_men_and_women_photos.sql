-- ---------------------------------------------------------------------
-- Two more per-project photo slots — men's attire and women's attire —
-- shown next to their notes columns in the invitation page's Attire
-- section. Same shape as the three existing PROJECT slots (cover /
-- ceremony / reception) added in V19 and V20: one nullable FK column
-- per slot, ON DELETE SET NULL, all sharing AttachmentOwnerType.PROJECT
-- (the FK column distinguishes them, not the owner type).
-- ---------------------------------------------------------------------

ALTER TABLE projects ADD COLUMN attire_men_photo_attachment_id   uuid;
ALTER TABLE projects ADD COLUMN attire_women_photo_attachment_id uuid;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_attire_men_photo_attachment
    FOREIGN KEY (attire_men_photo_attachment_id) REFERENCES attachments (id) ON DELETE SET NULL;

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_attire_women_photo_attachment
    FOREIGN KEY (attire_women_photo_attachment_id) REFERENCES attachments (id) ON DELETE SET NULL;
