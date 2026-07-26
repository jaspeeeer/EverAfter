-- ---------------------------------------------------------------------
-- Vendor payment history (installments). A vendor's agreed price is the
-- full amount to be paid; payments recorded against it can be a single
-- full payment or several installments. The managed budget line tracks
-- how much has been paid via a new expenses.paid_amount column.
-- ---------------------------------------------------------------------

CREATE TABLE vendor_payments (
    id         uuid           NOT NULL,
    vendor_id  uuid           NOT NULL,
    amount     numeric(12, 2) NOT NULL,
    paid_on    date           NOT NULL,
    note       varchar(255),
    CONSTRAINT pk_vendor_payments PRIMARY KEY (id),
    CONSTRAINT fk_vendor_payments_vendor
        FOREIGN KEY (vendor_id) REFERENCES vendors (id) ON DELETE CASCADE
);

CREATE INDEX ix_vendor_payments_vendor ON vendor_payments (vendor_id);

-- How much of each expense has been paid. For regular lines this is the amount when paid,
-- else 0; for a vendor's managed line it is the sum of that vendor's payments.
ALTER TABLE expenses ADD COLUMN paid_amount numeric(12, 2) NOT NULL DEFAULT 0;
UPDATE expenses SET paid_amount = amount WHERE paid = true;

-- Preserve history: a vendor line that was already fully paid becomes one full payment record.
INSERT INTO vendor_payments (id, vendor_id, amount, paid_on, note)
SELECT gen_random_uuid(), e.vendor_id, e.amount, CURRENT_DATE, 'Migrated payment'
FROM expenses e
WHERE e.managed = true AND e.paid = true AND e.vendor_id IS NOT NULL;
