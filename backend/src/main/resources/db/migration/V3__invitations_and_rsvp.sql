-- ---------------------------------------------------------------------
-- Guest extras: seating assignment + per-guest public RSVP token
-- ---------------------------------------------------------------------
ALTER TABLE guests ADD COLUMN table_number integer;
ALTER TABLE guests ADD COLUMN rsvp_token uuid NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX ux_guests_rsvp_token ON guests (rsvp_token);

-- ---------------------------------------------------------------------
-- invitations : planner invites a couple to own a project
-- ---------------------------------------------------------------------
CREATE TABLE invitations (
    id          uuid         NOT NULL,
    email       varchar(255) NOT NULL,
    token       uuid         NOT NULL,
    status      varchar(20)  NOT NULL DEFAULT 'PENDING',
    project_id  uuid         NOT NULL,
    created_at  timestamp    NOT NULL,
    accepted_at timestamp,
    CONSTRAINT pk_invitations PRIMARY KEY (id),
    CONSTRAINT ux_invitations_token UNIQUE (token),
    CONSTRAINT fk_invitations_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX ix_invitations_project ON invitations (project_id);
