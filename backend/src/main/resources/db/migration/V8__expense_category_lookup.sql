-- ---------------------------------------------------------------------
-- Unify expense categories onto the admin-managed vendor_categories
-- lookup (introduced in V6), so a single managed list backs every
-- category dropdown. The old ExpenseCategory enum column is backfilled
-- to the matching category slug (no data loss): FLOWERS maps to the
-- FLORIST category, GIFTS has no vendor equivalent so it maps to OTHER,
-- every other value shares its slug.
-- ---------------------------------------------------------------------

ALTER TABLE expenses ADD COLUMN category_id uuid;

UPDATE expenses e SET category_id = c.id
    FROM vendor_categories c
    WHERE c.slug = CASE e.category
        WHEN 'FLOWERS' THEN 'FLORIST'
        WHEN 'GIFTS'   THEN 'OTHER'
        ELSE e.category
    END;

-- Safety net: anything left unmapped falls back to OTHER.
UPDATE expenses SET category_id = (SELECT id FROM vendor_categories WHERE slug = 'OTHER')
    WHERE category_id IS NULL;

ALTER TABLE expenses ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_category
    FOREIGN KEY (category_id) REFERENCES vendor_categories (id);
ALTER TABLE expenses DROP COLUMN category;
CREATE INDEX ix_expenses_category ON expenses (category_id);
