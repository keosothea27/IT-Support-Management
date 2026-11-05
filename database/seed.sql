-- Optional sample data (same as the demo in the browser-only version). Run after schema.sql:
--   psql -U itdesk -d itdesk -f seed.sql
-- Dates are relative to the day you run it. No users are created here: the Java server creates the
-- first Administrator on start-up (see README), and you add the rest on the "User Management" screen.

BEGIN;

INSERT INTO departments (id, name) VALUES
 ('DEP-01', 'IT'), ('DEP-02', 'Accounting'), ('DEP-03', 'HR'), ('DEP-04', 'Security'), ('DEP-05', 'Operations');

INSERT INTO locations (id, name, address) VALUES
 ('LOC-01', 'Head Office', 'Floor 1'), ('LOC-02', 'Branch 1', 'Ground floor'), ('LOC-03', 'Gate 1', 'Security booth');

INSERT INTO vendors (id, name, contact, phone, email, service) VALUES
 ('VEN-01', 'TechParts Co', 'Contact A', '000-000', 'sales@example.com', 'Hardware repair'),
 ('VEN-02', 'NetWorks Ltd', 'Contact B', '000-001', 'help@example.com', 'Network support');

INSERT INTO employees (id, name, dept, location, email, status) VALUES
 ('EMP-001', 'Employee A', 'Accounting', 'Head Office', 'a@example.com', 'Active'),
 ('EMP-002', 'Employee B', 'HR', 'Head Office', 'b@example.com', 'Active'),
 ('EMP-003', 'IT Administrator', 'IT', 'Head Office', 'it@example.com', 'Active'),
 ('EMP-004', 'Security Team', 'Security', 'Gate 1', 'sec@example.com', 'Active'),
 ('EMP-005', 'Former Employee', 'Operations', 'Branch 1', 'x@example.com', 'Inactive');

INSERT INTO categories (id, name, type, sla) VALUES
 ('CAT-01', 'Hardware', 'Ticket category', NULL), ('CAT-02', 'Software', 'Ticket category', NULL),
 ('CAT-03', 'Network', 'Ticket category', NULL), ('CAT-04', 'Account & Access', 'Ticket category', NULL),
 ('CAT-05', 'Other', 'Ticket category', NULL),
 ('CAT-06', 'Low', 'Priority', 72), ('CAT-07', 'Medium', 'Priority', 24),
 ('CAT-08', 'High', 'Priority', 8), ('CAT-09', 'Critical', 'Priority', 2);

-- warranty offsets (days from today) and purchase dates step back 30 days per row
INSERT INTO assets (id, name, category, dept, owner, status, warranty, location, purchase) VALUES
 ('IT-0001', 'Lenovo Laptop',      'Laptop',       'Accounting', 'Employee A',       'In Use',       CURRENT_DATE + 200, 'Head Office', CURRENT_DATE - 10),
 ('IT-0002', 'Dell Desktop',       'Desktop',      'IT',         NULL,               'In Stock',     CURRENT_DATE + 120, 'Head Office', CURRENT_DATE - 40),
 ('IT-0003', 'Dell Monitor',       'Monitor',      'HR',         'Employee B',       'In Use',       CURRENT_DATE + 400, 'Head Office', CURRENT_DATE - 70),
 ('IT-0004', 'Logitech Mouse',     'Peripheral',   'Accounting', 'Employee A',       'In Use',       CURRENT_DATE + 300, 'Head Office', CURRENT_DATE - 100),
 ('IT-0005', 'HP Printer',         'Printer',      'HR',         'Employee B',       'In Use',       CURRENT_DATE + 20,  'Head Office', CURRENT_DATE - 130),
 ('IT-0006', 'Epson Scanner',      'Scanner',      'HR',         NULL,               'Under Repair', CURRENT_DATE - 30,  'Head Office', CURRENT_DATE - 160),
 ('IT-0007', 'Dell Server',        'Server',       'IT',         'IT Administrator', 'In Use',       CURRENT_DATE + 600, 'Head Office', CURRENT_DATE - 190),
 ('IT-0008', 'Ruijie Switch',      'Switch',       'IT',         'IT Administrator', 'In Use',       CURRENT_DATE + 400, 'Head Office', CURRENT_DATE - 220),
 ('IT-0009', 'Ruijie Router',      'Router',       'IT',         'IT Administrator', 'In Use',       CURRENT_DATE + 25,  'Head Office', CURRENT_DATE - 250),
 ('IT-0010', 'Ruijie Access Point','Access Point', 'IT',         NULL,               'In Use',       CURRENT_DATE + 380, 'Head Office', CURRENT_DATE - 280),
 ('IT-0011', 'Sophos Firewall',    'Firewall',     'IT',         'IT Administrator', 'In Use',       CURRENT_DATE + 150, 'Head Office', CURRENT_DATE - 310),
 ('IT-0012', 'Hikvision Camera',   'CCTV',         'Security',   NULL,               'In Use',       CURRENT_DATE + 500, 'Head Office', CURRENT_DATE - 340),
 ('IT-0013', 'Hikvision NVR',      'NVR',          'Security',   'Security Team',    'In Use',       CURRENT_DATE + 15,  'Head Office', CURRENT_DATE - 370),
 ('IT-0014', 'Old Laptop',         'Laptop',       'Operations', NULL,               'Retired',      CURRENT_DATE - 400, 'Head Office', CURRENT_DATE - 400);

-- 12 tickets, newest first, 17 days apart
INSERT INTO tickets (id, requester, category, description, priority, status, tech, created)
SELECT 'TKT-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad((i + 1)::text, 4, '0'),
       (ARRAY['Employee A', 'Employee B', 'Security Team'])[i % 3 + 1],
       (ARRAY['Hardware', 'Software', 'Network', 'Account & Access', 'Other'])[i % 5 + 1],
       (ARRAY['Laptop will not start', 'Office activation problem', 'Wi-Fi access point is down', 'Password reset needed', 'Printer needs a new toner'])[i % 5 + 1],
       (ARRAY['Low', 'Medium', 'High', 'Critical'])[(i + 1) % 4 + 1],
       (ARRAY['Open', 'Assigned', 'In Progress', 'Resolved', 'Closed', 'Pending'])[i % 6 + 1],
       CASE WHEN i % 4 <> 0 THEN 'IT Staff 01' END,
       CURRENT_DATE - i * 17
FROM generate_series(0, 11) AS i;

INSERT INTO ticket_comments (id, ticket, author, comment, date) VALUES
 ('CMT-001', 'TKT-' || to_char(CURRENT_DATE, 'YYYY') || '-0001', 'IT Staff 01', 'Checking the device now.',  CURRENT_DATE),
 ('CMT-002', 'TKT-' || to_char(CURRENT_DATE, 'YYYY') || '-0002', 'IT Staff 01', 'Waiting for a spare part.', CURRENT_DATE - 17);

INSERT INTO licenses (id, software, version, type, seats, expires) VALUES
 ('LIC-001', 'Microsoft Office',     '2021',   'Volume',       50,  CURRENT_DATE + 45),
 ('LIC-002', 'Sophos Endpoint',      'Latest', 'Subscription', 120, CURRENT_DATE + 300),
 ('LIC-003', 'Hikvision iVMS-4200',  '3.x',    'Free',         5,   CURRENT_DATE + 900);

INSERT INTO software_records (id, software, type, target, date, cost) VALUES
 ('SWR-001', 'Microsoft Office', 'Assignment',   'Employee A',    CURRENT_DATE - 60,  NULL),
 ('SWR-002', 'Microsoft Office', 'Installation', 'Lenovo Laptop', CURRENT_DATE - 60,  NULL),
 ('SWR-003', 'Sophos Endpoint',  'Installation', 'Dell Desktop',  CURRENT_DATE - 30,  NULL),
 ('SWR-004', 'Microsoft Office', 'Renewal',      'Company',       CURRENT_DATE - 330, 1200);

INSERT INTO asset_movements (id, asset, type, from_party, to_party, date) VALUES
 ('MOV-001', 'IT-0001 Lenovo Laptop', 'Assignment', 'IT',         'Employee A', CURRENT_DATE - 90),
 ('MOV-002', 'IT-0002 Dell Desktop',  'Transfer',   'Accounting', 'IT',         CURRENT_DATE - 20),
 ('MOV-003', 'IT-0014 Old Laptop',    'Return',     'Operations', 'IT',         CURRENT_DATE - 40);

INSERT INTO maintenance_jobs (id, asset, type, vendor, date, status, tech, cost, parts) VALUES
 ('MNT-001', 'IT-0006 Epson Scanner', 'Repair',         'TechParts Co', CURRENT_DATE - 5,  'In Progress', 'IT Staff 01', 80,  'Feed roller'),
 ('MNT-002', 'IT-0007 Dell Server',   'Preventive',     'NetWorks Ltd', CURRENT_DATE + 14, 'Scheduled',   'IT Staff 01', 0,   NULL),
 ('MNT-003', 'IT-0008 Ruijie Switch', 'Infrastructure', 'NetWorks Ltd', CURRENT_DATE - 90, 'Done',        'IT Staff 01', 150, 'Fan');

INSERT INTO ip_addresses (id, ip, device, vlan, location, status) VALUES
 ('IP-001', '10.0.0.1',  'Ruijie Router', '1',  'Head Office', 'Used'),
 ('IP-002', '10.0.0.10', 'Dell Server',   '10', 'Head Office', 'Used'),
 ('IP-003', '10.0.0.50', NULL,            '10', 'Head Office', 'Free');

INSERT INTO network_docs (id, title, type) VALUES
 ('DOC-01', 'Head office network diagram', 'Diagram'),
 ('DOC-02', 'Switch replacement procedure', 'Procedure');

-- the id sequences continue after the sample rows
SELECT setval('seq_departments', 5), setval('seq_locations', 3), setval('seq_vendors', 2), setval('seq_employees', 5),
       setval('seq_categories', 9), setval('seq_assets', 14), setval('seq_tickets', 12), setval('seq_ticket_comments', 2),
       setval('seq_licenses', 3), setval('seq_software_records', 4), setval('seq_asset_movements', 3),
       setval('seq_maintenance_jobs', 3), setval('seq_ip_addresses', 3), setval('seq_network_docs', 2);

COMMIT;
