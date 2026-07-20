-- =====================================================================
-- V1 :: Wedding Planner - initial schema (Phase 1)
-- This migration is the SOURCE OF TRUTH for the database schema.
-- JPA runs with ddl-auto: validate, so entities must match these tables.
-- =====================================================================

-- ---------------------------------------------------------------------
-- roles : one row per RBAC role (ROLE_ADMIN, ROLE_PLANNER, ROLE_USER)
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id   uuid        NOT NULL,
    name varchar(32) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

-- ---------------------------------------------------------------------
-- users : platform accounts
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id            uuid         NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    first_name    varchar(100),
    last_name     varchar(100),
    enabled       boolean      NOT NULL DEFAULT true,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------
-- user_roles : many-to-many between users and roles
-- ---------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- ---------------------------------------------------------------------
-- projects : the wedding project aggregate root
--   planner_id -> managing planner (many projects per planner)
--   owner_id   -> couple/user account; UNIQUE enforces "one project per couple"
-- ---------------------------------------------------------------------
CREATE TABLE projects (
    id           uuid          NOT NULL,
    name         varchar(200)  NOT NULL,
    wedding_date date,
    total_budget numeric(12, 2),
    planner_id   uuid          NOT NULL,
    owner_id     uuid,
    created_at   timestamp     NOT NULL,
    updated_at   timestamp     NOT NULL,
    CONSTRAINT pk_projects PRIMARY KEY (id),
    CONSTRAINT uq_projects_owner UNIQUE (owner_id),
    CONSTRAINT fk_projects_planner FOREIGN KEY (planner_id) REFERENCES users (id),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX ix_projects_planner ON projects (planner_id);

-- ---------------------------------------------------------------------
-- tasks : kanban checklist items
-- ---------------------------------------------------------------------
CREATE TABLE tasks (
    id          uuid         NOT NULL,
    title       varchar(200) NOT NULL,
    description text,
    status      varchar(20)  NOT NULL,
    due_date    date,
    project_id  uuid         NOT NULL,
    CONSTRAINT pk_tasks PRIMARY KEY (id),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX ix_tasks_project ON tasks (project_id);

-- ---------------------------------------------------------------------
-- vendors : suppliers for a wedding
-- ---------------------------------------------------------------------
CREATE TABLE vendors (
    id            uuid         NOT NULL,
    name          varchar(200) NOT NULL,
    category      varchar(20)  NOT NULL,
    contact_email varchar(255),
    phone         varchar(40),
    booked        boolean      NOT NULL DEFAULT false,
    project_id    uuid         NOT NULL,
    CONSTRAINT pk_vendors PRIMARY KEY (id),
    CONSTRAINT fk_vendors_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX ix_vendors_project ON vendors (project_id);

-- ---------------------------------------------------------------------
-- expenses : budget line items
-- ---------------------------------------------------------------------
CREATE TABLE expenses (
    id          uuid          NOT NULL,
    description varchar(255)  NOT NULL,
    amount      numeric(12, 2) NOT NULL,
    category    varchar(20)   NOT NULL,
    paid        boolean       NOT NULL DEFAULT false,
    project_id  uuid          NOT NULL,
    CONSTRAINT pk_expenses PRIMARY KEY (id),
    CONSTRAINT fk_expenses_project FOREIGN KEY (project_id) REFERENCES projects (id)
);

CREATE INDEX ix_expenses_project ON expenses (project_id);
