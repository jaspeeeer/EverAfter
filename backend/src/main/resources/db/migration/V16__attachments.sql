-- ---------------------------------------------------------------------
-- V16 :: Attachments (contracts, receipts, quotes) hung off vendors,
-- vendor payments, and expenses.
--
-- The owner reference is polymorphic — (owner_type, owner_id) — because
-- a single SQL column can't FK to three tables. project_id is denorm'd
-- alongside so RBAC (canAccess/canManage) stays a cheap indexed lookup
-- and ON DELETE CASCADE from projects sweeps orphan rows on project
-- deletion. Owner-side cleanup (vendor/payment/expense delete → drop
-- their attachments) is enforced application-side by AttachmentService.
-- ---------------------------------------------------------------------

CREATE TABLE attachments (
    id             uuid         NOT NULL,
    project_id     uuid         NOT NULL,
    owner_type     varchar(24)  NOT NULL,     -- VENDOR | VENDOR_PAYMENT | EXPENSE
    owner_id       uuid         NOT NULL,
    filename       varchar(255) NOT NULL,
    content_type   varchar(100) NOT NULL,
    size_bytes     bigint       NOT NULL,
    storage_key    varchar(500) NOT NULL,
    uploaded_by    uuid,
    uploaded_at    timestamp    NOT NULL DEFAULT now(),
    CONSTRAINT pk_attachments PRIMARY KEY (id),
    CONSTRAINT fk_attachments_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_attachments_uploader
        FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX ix_attachments_owner   ON attachments (owner_type, owner_id);
CREATE INDEX ix_attachments_project ON attachments (project_id);
