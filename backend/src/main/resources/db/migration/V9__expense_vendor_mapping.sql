-- ---------------------------------------------------------------------
-- Expenses can now be mapped to a project vendor by the user. The
-- vendor_id column already existed for the auto-synced agreed-price line
-- (V7); a new `managed` flag distinguishes that system-owned line (kept
-- read-only) from a user's manual mapping.
-- ---------------------------------------------------------------------

ALTER TABLE expenses ADD COLUMN managed boolean NOT NULL DEFAULT false;

-- Every existing vendor-linked expense is an auto-synced agreed-price line.
UPDATE expenses SET managed = true WHERE vendor_id IS NOT NULL;

-- A user's manual mapping should survive vendor deletion (unmap, keep the expense).
-- The managed auto-synced line is removed explicitly by VendorService instead of by
-- cascade, so switch the vendor FK from ON DELETE CASCADE to ON DELETE SET NULL.
ALTER TABLE expenses DROP CONSTRAINT fk_expenses_vendor;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_vendor
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) ON DELETE SET NULL;
