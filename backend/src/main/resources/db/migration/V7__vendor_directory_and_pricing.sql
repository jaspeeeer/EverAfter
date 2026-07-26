-- ---------------------------------------------------------------------
-- Global vendor directory (admin-curated), vendor agreed price, and the
-- expense link that lets an agreed price feed the budget.
-- ---------------------------------------------------------------------

CREATE TABLE vendor_directory (
    id            uuid         NOT NULL,
    name          varchar(200) NOT NULL,
    category_id   uuid         NOT NULL,
    contact_email varchar(255),
    phone         varchar(40),
    typical_price numeric(12, 2),
    notes         varchar(500),
    active        boolean      NOT NULL DEFAULT true,
    CONSTRAINT pk_vendor_directory PRIMARY KEY (id),
    CONSTRAINT fk_vendor_directory_category
        FOREIGN KEY (category_id) REFERENCES vendor_categories (id)
);

CREATE INDEX ix_vendor_directory_category ON vendor_directory (category_id);

-- vendors: agreed ("done deal") price + optional link to a directory entry.
ALTER TABLE vendors ADD COLUMN agreed_price numeric(12, 2);
ALTER TABLE vendors ADD COLUMN directory_id uuid;
ALTER TABLE vendors ADD CONSTRAINT fk_vendors_directory
    FOREIGN KEY (directory_id) REFERENCES vendor_directory (id) ON DELETE SET NULL;
CREATE INDEX ix_vendors_directory ON vendors (directory_id);

-- expenses: the auto-synced budget line for a vendor's agreed price.
-- ON DELETE CASCADE so deleting a vendor removes its linked expense.
ALTER TABLE expenses ADD COLUMN vendor_id uuid;
ALTER TABLE expenses ADD CONSTRAINT fk_expenses_vendor
    FOREIGN KEY (vendor_id) REFERENCES vendors (id) ON DELETE CASCADE;
CREATE INDEX ix_expenses_vendor ON expenses (vendor_id);
