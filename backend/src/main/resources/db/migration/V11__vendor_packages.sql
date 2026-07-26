-- ---------------------------------------------------------------------
-- Package vendors: a vendor can bundle other vendors under it (e.g. a
-- coordinator package bundling catering + photography + flowers). A
-- "package" is simply a top-level vendor (parent_id IS NULL) that has
-- children; a child vendor has parent_id set. One level of nesting only —
-- a child's parent must itself be top-level (enforced in VendorService).
--
-- Money lives ONLY on the package (or a standalone vendor): a child never
-- carries an agreed price, payments, or a managed budget line, which is
-- what keeps the budget and admin reports from double-counting.
-- ---------------------------------------------------------------------

ALTER TABLE vendors ADD COLUMN parent_id uuid;
ALTER TABLE vendors ADD CONSTRAINT fk_vendors_parent
    FOREIGN KEY (parent_id) REFERENCES vendors (id) ON DELETE CASCADE;
CREATE INDEX ix_vendors_parent ON vendors (parent_id);
