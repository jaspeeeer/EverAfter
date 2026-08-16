-- ---------------------------------------------------------------------
-- A guest may now carry zero, one, or several guest_roles — replaces the
-- single guests.role_id FK with a join table shaped exactly like
-- timeline_event_vendors (V5): composite PK, both FKs ON DELETE CASCADE,
-- no surrogate id, no extra indexes.
-- ---------------------------------------------------------------------

CREATE TABLE guest_role_assignments (
    guest_id uuid NOT NULL,
    role_id  uuid NOT NULL,
    CONSTRAINT pk_guest_role_assignments PRIMARY KEY (guest_id, role_id),
    CONSTRAINT fk_gra_guest FOREIGN KEY (guest_id) REFERENCES guests (id)      ON DELETE CASCADE,
    CONSTRAINT fk_gra_role  FOREIGN KEY (role_id)  REFERENCES guest_roles (id) ON DELETE CASCADE
);

-- Carry each existing single role forward (including soft-deleted guests, so a restore
-- keeps its role — Postgres doesn't know about the JPA @SQLRestriction, and that's
-- exactly the behavior we want here).
INSERT INTO guest_role_assignments (guest_id, role_id)
SELECT id, role_id FROM guests WHERE role_id IS NOT NULL;

ALTER TABLE guests DROP CONSTRAINT fk_guests_role;
DROP INDEX IF EXISTS ix_guests_role;
ALTER TABLE guests DROP COLUMN role_id;
