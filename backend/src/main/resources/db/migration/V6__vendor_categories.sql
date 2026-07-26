-- ---------------------------------------------------------------------
-- Vendor categories become admin-managed data: a lookup table that
-- vendors and vendor-template items reference by FK. Existing rows are
-- backfilled from the old enum-string column (no data loss).
-- ---------------------------------------------------------------------

CREATE TABLE vendor_categories (
    id         uuid        NOT NULL,
    name       varchar(60) NOT NULL,
    slug       varchar(40) NOT NULL,
    active     boolean     NOT NULL DEFAULT true,
    sort_order integer     NOT NULL,
    CONSTRAINT pk_vendor_categories PRIMARY KEY (id),
    CONSTRAINT ux_vendor_categories_name UNIQUE (name),
    CONSTRAINT ux_vendor_categories_slug UNIQUE (slug)
);

-- Seed the 11 values the enum used to hold (slug = old enum name).
INSERT INTO vendor_categories (id, name, slug, active, sort_order) VALUES
    (gen_random_uuid(), 'Venue',       'VENUE',       true, 0),
    (gen_random_uuid(), 'Catering',    'CATERING',    true, 1),
    (gen_random_uuid(), 'Photography', 'PHOTOGRAPHY', true, 2),
    (gen_random_uuid(), 'Videography', 'VIDEOGRAPHY', true, 3),
    (gen_random_uuid(), 'Florist',     'FLORIST',     true, 4),
    (gen_random_uuid(), 'Music',       'MUSIC',       true, 5),
    (gen_random_uuid(), 'Attire',      'ATTIRE',      true, 6),
    (gen_random_uuid(), 'Beauty',      'BEAUTY',      true, 7),
    (gen_random_uuid(), 'Stationery',  'STATIONERY',  true, 8),
    (gen_random_uuid(), 'Transport',   'TRANSPORT',   true, 9),
    (gen_random_uuid(), 'Other',       'OTHER',       true, 10);

-- vendors: swap the varchar enum column for a category FK.
ALTER TABLE vendors ADD COLUMN category_id uuid;
UPDATE vendors v SET category_id = c.id
    FROM vendor_categories c WHERE c.slug = v.category;
ALTER TABLE vendors ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE vendors ADD CONSTRAINT fk_vendors_category
    FOREIGN KEY (category_id) REFERENCES vendor_categories (id);
ALTER TABLE vendors DROP COLUMN category;
CREATE INDEX ix_vendors_category ON vendors (category_id);

-- vendor_template_items: same swap.
ALTER TABLE vendor_template_items ADD COLUMN category_id uuid;
UPDATE vendor_template_items i SET category_id = c.id
    FROM vendor_categories c WHERE c.slug = i.category;
ALTER TABLE vendor_template_items ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE vendor_template_items ADD CONSTRAINT fk_vti_category
    FOREIGN KEY (category_id) REFERENCES vendor_categories (id);
ALTER TABLE vendor_template_items DROP COLUMN category;
CREATE INDEX ix_vti_category ON vendor_template_items (category_id);
