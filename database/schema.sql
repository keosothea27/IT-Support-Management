-- IT Management System - PostgreSQL 13+ schema
-- Run once on an empty database:  psql -U itdesk -d itdesk -f schema.sql
-- Ids are text such as IT-0001 or TKT-2026-0007; the Java server builds them from the seq_* sequences.
-- Columns that hold names (dept, owner, location...) are plain text on purpose: the screens work with names.

BEGIN;

-- ---------- sign-in, settings, audit ----------
CREATE TABLE app_users (
    id            TEXT PRIMARY KEY,
    name          TEXT NOT NULL,
    email         TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('Administrator', 'IT Technician', 'Employee')),
    status        TEXT NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Disabled')),
    password_hash TEXT                                   -- PBKDF2, never sent to the browser
);
CREATE UNIQUE INDEX ux_app_users_email ON app_users (lower(email));
CREATE UNIQUE INDEX ux_app_users_name  ON app_users (lower(name));   -- employees see tickets by name

CREATE TABLE sessions (
    token_hash TEXT PRIMARY KEY,                         -- SHA-256 of the bearer token
    user_id    TEXT NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_sessions_expires ON sessions (expires_at);

CREATE TABLE settings (
    setting_key   TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL
);
INSERT INTO settings (setting_key, setting_value) VALUES ('co', 'IT Desk'), ('n1', '1'), ('n2', '1'), ('n3', '1');

CREATE TABLE audit_log (
    id        BIGSERIAL PRIMARY KEY,
    logged_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor     TEXT NOT NULL,
    role      TEXT NOT NULL,
    action    TEXT NOT NULL,
    entity    TEXT NOT NULL,
    record_id TEXT
);
CREATE INDEX ix_audit_log_at ON audit_log (logged_at DESC);

-- ---------- reference data ----------
CREATE TABLE departments (
    id   TEXT PRIMARY KEY,
    name TEXT NOT NULL
);
CREATE UNIQUE INDEX ux_departments_name ON departments (lower(name));

CREATE TABLE locations (
    id      TEXT PRIMARY KEY,
    name    TEXT NOT NULL,
    address TEXT
);
CREATE UNIQUE INDEX ux_locations_name ON locations (lower(name));

CREATE TABLE vendors (
    id      TEXT PRIMARY KEY,
    name    TEXT NOT NULL,
    contact TEXT,
    phone   TEXT,
    email   TEXT,
    service TEXT
);
CREATE UNIQUE INDEX ux_vendors_name ON vendors (lower(name));

CREATE TABLE employees (
    id       TEXT PRIMARY KEY,
    name     TEXT NOT NULL,
    dept     TEXT,
    location TEXT,
    email    TEXT,
    status   TEXT NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Inactive'))
);
CREATE UNIQUE INDEX ux_employees_email ON employees (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE categories (
    id   TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('Ticket category', 'Priority')),
    sla  INTEGER CHECK (sla IS NULL OR sla >= 0)         -- response target in hours
);

-- ---------- assets ----------
CREATE TABLE assets (
    id       TEXT PRIMARY KEY,
    name     TEXT NOT NULL,
    category TEXT CHECK (category IN ('Laptop', 'Desktop', 'Monitor', 'Peripheral', 'Printer', 'Scanner', 'Server',
                                      'Router', 'Switch', 'Access Point', 'Firewall', 'CCTV', 'NVR', 'UPS')),
    brand    TEXT,
    serial   TEXT,
    dept     TEXT,
    owner    TEXT,
    location TEXT,
    purchase DATE,
    price    NUMERIC(12, 2) CHECK (price IS NULL OR price >= 0),
    warranty DATE,
    status   TEXT NOT NULL DEFAULT 'In Stock' CHECK (status IN ('In Use', 'In Stock', 'Under Repair', 'Retired', 'Lost')),
    ip       TEXT,
    mac      TEXT,
    notes    TEXT
);
CREATE UNIQUE INDEX ux_assets_serial ON assets (serial) WHERE serial IS NOT NULL;
CREATE INDEX ix_assets_status   ON assets (status);
CREATE INDEX ix_assets_category ON assets (category);
CREATE INDEX ix_assets_warranty ON assets (warranty);

CREATE TABLE asset_movements (
    id         TEXT PRIMARY KEY,
    asset      TEXT NOT NULL,                            -- "IT-0001 Lenovo Laptop"
    type       TEXT CHECK (type IN ('Assignment', 'Transfer', 'Return')),
    from_party TEXT,
    to_party   TEXT,
    date       DATE,
    notes      TEXT
);

CREATE TABLE maintenance_jobs (
    id     TEXT PRIMARY KEY,
    asset  TEXT NOT NULL,
    type   TEXT CHECK (type IN ('Preventive', 'Repair', 'Infrastructure')),
    vendor TEXT,
    date   DATE,
    status TEXT CHECK (status IN ('Scheduled', 'In Progress', 'Done')),
    tech   TEXT,
    cost   NUMERIC(12, 2) CHECK (cost IS NULL OR cost >= 0),
    parts  TEXT,
    notes  TEXT
);

-- ---------- tickets ----------
CREATE TABLE tickets (
    id          TEXT PRIMARY KEY,
    requester   TEXT NOT NULL,
    category    TEXT CHECK (category IN ('Hardware', 'Software', 'Network', 'Account & Access', 'Other')),
    description TEXT NOT NULL,
    priority    TEXT NOT NULL DEFAULT 'Medium' CHECK (priority IN ('Low', 'Medium', 'High', 'Critical')),
    tech        TEXT,
    status      TEXT NOT NULL DEFAULT 'Open' CHECK (status IN ('Open', 'Assigned', 'In Progress', 'Pending', 'Resolved', 'Closed')),
    created     DATE NOT NULL DEFAULT CURRENT_DATE,
    hours       NUMERIC(8, 2) CHECK (hours IS NULL OR hours >= 0),
    resolution  TEXT
);
CREATE INDEX ix_tickets_status    ON tickets (status);
CREATE INDEX ix_tickets_requester ON tickets (requester);
CREATE INDEX ix_tickets_created   ON tickets (created);

CREATE TABLE ticket_comments (
    id      TEXT PRIMARY KEY,
    ticket  TEXT NOT NULL REFERENCES tickets (id) ON DELETE CASCADE,
    author  TEXT,
    comment TEXT NOT NULL,
    date    DATE NOT NULL DEFAULT CURRENT_DATE
);
CREATE INDEX ix_ticket_comments_ticket ON ticket_comments (ticket);

-- ---------- software ----------
CREATE TABLE licenses (
    id       TEXT PRIMARY KEY,
    software TEXT NOT NULL,
    version  TEXT,
    type     TEXT,
    seats    INTEGER CHECK (seats IS NULL OR seats >= 0),
    purchase DATE,
    expires  DATE,
    notes    TEXT                                        -- never store license keys here
);

CREATE TABLE software_records (
    id       TEXT PRIMARY KEY,
    software TEXT NOT NULL,
    type     TEXT CHECK (type IN ('Assignment', 'Installation', 'Renewal')),
    target   TEXT,
    date     DATE,
    cost     NUMERIC(12, 2) CHECK (cost IS NULL OR cost >= 0),
    notes    TEXT
);

-- ---------- network ----------
CREATE TABLE ip_addresses (
    id       TEXT PRIMARY KEY,
    ip       TEXT NOT NULL,
    device   TEXT,
    mac      TEXT,
    vlan     TEXT,
    location TEXT,
    status   TEXT CHECK (status IN ('Used', 'Free', 'Reserved'))
);
CREATE UNIQUE INDEX ux_ip_addresses_ip ON ip_addresses (ip);

CREATE TABLE network_docs (
    id    TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    type  TEXT CHECK (type IN ('Diagram', 'Procedure', 'Vendor', 'Other')),
    url   TEXT,
    notes TEXT
);

-- ---------- id sequences (one per table, named seq_<table>) ----------
CREATE SEQUENCE seq_app_users;
CREATE SEQUENCE seq_departments;
CREATE SEQUENCE seq_locations;
CREATE SEQUENCE seq_vendors;
CREATE SEQUENCE seq_employees;
CREATE SEQUENCE seq_categories;
CREATE SEQUENCE seq_assets;
CREATE SEQUENCE seq_asset_movements;
CREATE SEQUENCE seq_maintenance_jobs;
CREATE SEQUENCE seq_tickets;
CREATE SEQUENCE seq_ticket_comments;
CREATE SEQUENCE seq_licenses;
CREATE SEQUENCE seq_software_records;
CREATE SEQUENCE seq_ip_addresses;
CREATE SEQUENCE seq_network_docs;

COMMIT;
