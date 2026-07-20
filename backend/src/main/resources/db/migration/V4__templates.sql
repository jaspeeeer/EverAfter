-- ---------------------------------------------------------------------
-- Templates: admin-managed presets that planners apply to projects.
-- ---------------------------------------------------------------------

CREATE TABLE checklist_templates (
    id          uuid         NOT NULL,
    name        varchar(200) NOT NULL,
    description varchar(500),
    created_at  timestamp    NOT NULL,
    updated_at  timestamp    NOT NULL,
    CONSTRAINT pk_checklist_templates PRIMARY KEY (id)
);

CREATE TABLE checklist_template_items (
    id                  uuid         NOT NULL,
    template_id         uuid         NOT NULL,
    title               varchar(200) NOT NULL,
    description         varchar(500),
    days_before_wedding integer,
    sort_order          integer      NOT NULL,
    CONSTRAINT pk_checklist_template_items PRIMARY KEY (id),
    CONSTRAINT fk_cti_template FOREIGN KEY (template_id) REFERENCES checklist_templates (id)
);

CREATE INDEX ix_cti_template ON checklist_template_items (template_id);

CREATE TABLE vendor_templates (
    id          uuid         NOT NULL,
    name        varchar(200) NOT NULL,
    description varchar(500),
    created_at  timestamp    NOT NULL,
    updated_at  timestamp    NOT NULL,
    CONSTRAINT pk_vendor_templates PRIMARY KEY (id)
);

CREATE TABLE vendor_template_items (
    id          uuid         NOT NULL,
    template_id uuid         NOT NULL,
    name        varchar(200) NOT NULL,
    category    varchar(20)  NOT NULL,
    sort_order  integer      NOT NULL,
    CONSTRAINT pk_vendor_template_items PRIMARY KEY (id),
    CONSTRAINT fk_vti_template FOREIGN KEY (template_id) REFERENCES vendor_templates (id)
);

CREATE INDEX ix_vti_template ON vendor_template_items (template_id);
