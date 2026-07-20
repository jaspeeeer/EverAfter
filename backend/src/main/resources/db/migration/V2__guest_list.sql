-- ---------------------------------------------------------------------
-- guests : guest-list CRM entries for a wedding
-- ---------------------------------------------------------------------
CREATE TABLE guests (
    id            uuid         NOT NULL,
    name          varchar(200) NOT NULL,
    email         varchar(255),
    phone         varchar(40),
    rsvp_status   varchar(20)  NOT NULL DEFAULT 'PENDING',
    party_size    integer      NOT NULL DEFAULT 1,
    dietary_notes varchar(500),
    project_id    uuid         NOT NULL,
    CONSTRAINT pk_guests PRIMARY KEY (id),
    CONSTRAINT fk_guests_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX ix_guests_project ON guests (project_id);
