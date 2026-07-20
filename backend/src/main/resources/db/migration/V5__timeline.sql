-- ---------------------------------------------------------------------
-- Wedding-day timeline: events in time slots, each linked to the
-- suppliers (vendors) involved.
-- ---------------------------------------------------------------------

CREATE TABLE timeline_events (
    id          uuid         NOT NULL,
    project_id  uuid         NOT NULL,
    title       varchar(200) NOT NULL,
    description varchar(500),
    location    varchar(200),
    start_time  time         NOT NULL,
    end_time    time,
    CONSTRAINT pk_timeline_events PRIMARY KEY (id),
    CONSTRAINT fk_timeline_events_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX ix_timeline_events_project ON timeline_events (project_id);

-- Join table with DB-level cascades so deleting either side can never
-- orphan or block on the link rows, regardless of JPA flush ordering.
CREATE TABLE timeline_event_vendors (
    event_id  uuid NOT NULL,
    vendor_id uuid NOT NULL,
    CONSTRAINT pk_timeline_event_vendors PRIMARY KEY (event_id, vendor_id),
    CONSTRAINT fk_tev_event  FOREIGN KEY (event_id)  REFERENCES timeline_events (id) ON DELETE CASCADE,
    CONSTRAINT fk_tev_vendor FOREIGN KEY (vendor_id) REFERENCES vendors (id)        ON DELETE CASCADE
);
