-- ---------------------------------------------------------------------
-- V14 :: Extend vendor_payments from a paid-only ledger to a
-- planned-or-paid installment schedule so the reminder scheduler can
-- surface "vendor payment due soon" notifications.
--
-- Existing rows are treated as paid (paid=true, paid_on unchanged).
-- New rows may be planned: paid=false + due_date set + paid_on null.
-- A check constraint enforces the invariant so junk states can't sneak
-- in. VendorService.syncVendorExpense continues to sum only paid=true
-- amounts into expenses.paid_amount, so budget totals are unchanged.
-- ---------------------------------------------------------------------

ALTER TABLE vendor_payments
    ADD COLUMN due_date date;

ALTER TABLE vendor_payments
    ADD COLUMN paid boolean NOT NULL DEFAULT true;

-- Allow the paid_on column to be null for planned (unpaid) rows.
ALTER TABLE vendor_payments
    ALTER COLUMN paid_on DROP NOT NULL;

-- A row is either paid (paid_on required, due_date optional) or planned
-- (due_date required, paid_on must be null).
ALTER TABLE vendor_payments
    ADD CONSTRAINT chk_vendor_payment_state
    CHECK (
        (paid = true  AND paid_on IS NOT NULL) OR
        (paid = false AND paid_on IS NULL AND due_date IS NOT NULL)
    );

CREATE INDEX ix_vendor_payments_due_date
    ON vendor_payments (due_date)
    WHERE paid = false;
