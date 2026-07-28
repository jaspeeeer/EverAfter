-- ---------------------------------------------------------------------
-- Guest classification: three fixed enums (priority, related-to,
-- relationship) stored as string columns like rsvp_status, plus an
-- admin-managed `role` lookup (guest_roles) referenced by FK — the same
-- shape as vendor_categories (V6). All four are optional/nullable, so
-- existing minimal guest rows and imports are unaffected.
-- ---------------------------------------------------------------------

-- priority is a single char (A/B/C); a length-1 @Enumerated(STRING) column maps to char(1)
-- under ddl-auto: validate, so use char(1) (not varchar(1)) to match.
ALTER TABLE guests ADD COLUMN priority     char(1);
ALTER TABLE guests ADD COLUMN related_to   varchar(10);
ALTER TABLE guests ADD COLUMN relationship varchar(30);

CREATE TABLE guest_roles (
    id         uuid        NOT NULL,
    name       varchar(60) NOT NULL,
    slug       varchar(40) NOT NULL,
    active     boolean     NOT NULL DEFAULT true,
    sort_order integer     NOT NULL,
    CONSTRAINT pk_guest_roles PRIMARY KEY (id),
    CONSTRAINT ux_guest_roles_name UNIQUE (name),
    CONSTRAINT ux_guest_roles_slug UNIQUE (slug)
);

-- Starter roles admins can extend/rename/deactivate.
INSERT INTO guest_roles (id, name, slug, active, sort_order) VALUES
    (gen_random_uuid(), 'Principal Sponsor', 'PRINCIPAL_SPONSOR', true, 0),
    (gen_random_uuid(), 'Secondary Sponsor', 'SECONDARY_SPONSOR', true, 1),
    (gen_random_uuid(), 'Best Man',          'BEST_MAN',          true, 2),
    (gen_random_uuid(), 'Maid of Honor',     'MAID_OF_HONOR',     true, 3),
    (gen_random_uuid(), 'Bridesmaid',        'BRIDESMAID',        true, 4),
    (gen_random_uuid(), 'Groomsman',         'GROOMSMAN',         true, 5),
    (gen_random_uuid(), 'Parents',           'PARENTS',           true, 6),
    (gen_random_uuid(), 'Officiating Pastor','OFFICIATING_PASTOR',true, 7),
    (gen_random_uuid(), 'Ring Bearer',       'RING_BEARER',       true, 8),
    (gen_random_uuid(), 'Flower Girl',       'FLOWER_GIRL',       true, 9),
    (gen_random_uuid(), 'Bearer',            'BEARER',            true, 10),
    (gen_random_uuid(), 'Guest',             'GUEST',             true, 11);

ALTER TABLE guests ADD COLUMN role_id uuid;
ALTER TABLE guests ADD CONSTRAINT fk_guests_role
    FOREIGN KEY (role_id) REFERENCES guest_roles (id);
CREATE INDEX ix_guests_role ON guests (role_id);
