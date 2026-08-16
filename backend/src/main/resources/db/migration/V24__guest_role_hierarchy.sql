-- ---------------------------------------------------------------------
-- One level of nesting only — a child's parent must itself be top-level
-- (enforced in GuestRoleService, mirroring Vendor's package-item pattern
-- from V11). Organizes the Secondary Sponsor category into distinct
-- sub-roles per Filipino Catholic-wedding tradition.
-- ---------------------------------------------------------------------

ALTER TABLE guest_roles ADD COLUMN parent_id uuid;
ALTER TABLE guest_roles ADD CONSTRAINT fk_guest_roles_parent
    FOREIGN KEY (parent_id) REFERENCES guest_roles (id) ON DELETE SET NULL;
CREATE INDEX ix_guest_roles_parent ON guest_roles (parent_id);

-- Rename to plural to match the couple's intent, keeping the stable slug.
UPDATE guest_roles SET name = 'Flower Girls' WHERE slug = 'FLOWER_GIRL';

-- Reparent existing Ring Bearer and Flower Girls under Secondary Sponsor —
-- same UUIDs, so guests already tagged with these roles are unaffected.
UPDATE guest_roles
   SET parent_id = (SELECT id FROM guest_roles WHERE slug = 'SECONDARY_SPONSOR')
 WHERE slug IN ('RING_BEARER', 'FLOWER_GIRL');

-- Six new sub-roles under Secondary Sponsor, entourage-eligible.
INSERT INTO guest_roles (id, name, slug, active, sort_order, entourage_eligible, parent_id)
SELECT gen_random_uuid(), name, slug, true, sort_order, true,
       (SELECT id FROM guest_roles WHERE slug = 'SECONDARY_SPONSOR')
  FROM (VALUES
        ('Candle',         'CANDLE',         12),
        ('Veil',           'VEIL',           13),
        ('Cord',           'CORD',           14),
        ('Arrhae Bearer',  'ARRHAE_BEARER',  15),
        ('Rosary Bearer',  'ROSARY_BEARER',  16),
        ('Bible Bearer',   'BIBLE_BEARER',   17)
       ) AS v(name, slug, sort_order);
