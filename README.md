# IT Management System

Your single `index.html` split into separate layers:

```
frontend/   index.html, css/style.css, js/*.js     (what runs in the browser)
backend/    Java 17+ server, JDK only + PostgreSQL driver
database/   schema.sql (tables), seed.sql (optional sample data)
```

The browser talks to the Java server (`/api/...`), and the Java server talks to PostgreSQL.
If the Java server is not running, `frontend/index.html` still works on its own in **local mode**
(sample data kept in the browser, exactly like your original file).

## Run it

1. **Database** (PostgreSQL 13+)
   ```
   createuser itdesk --pwprompt
   createdb itdesk --owner itdesk
   psql -U itdesk -d itdesk -f database/schema.sql
   psql -U itdesk -d itdesk -f database/seed.sql      # optional sample data
   ```
2. **Backend** (needs JDK 17+ and Maven)
   ```
   cd backend
   mvn package
   export DB_URL=jdbc:postgresql://localhost:5432/itdesk DB_USER=itdesk DB_PASSWORD=...
   export ADMIN_EMAIL=you@company.com ADMIN_PASSWORD='at-least-10-characters'
   java -cp "target/classes:target/lib/*" com.itdesk.App
   ```
   Open http://127.0.0.1:8080 and sign in. If you leave `ADMIN_PASSWORD` out, a random one is printed once.
   Add the other users on **Administration > User Management** (each needs a password of 10+ characters).

| Setting | Default |
|---|---|
| `PORT` | 8080 |
| `BIND` | 127.0.0.1 (only this computer). Use `0.0.0.0` only behind an HTTPS reverse proxy. |
| `FRONTEND_DIR` | ../frontend |

## Tests
```
cd backend
mkdir -p /tmp/o /tmp/t
javac -d /tmp/o src/main/java/com/itdesk/*.java
javac -cp /tmp/o -d /tmp/t src/test/java/com/itdesk/SelfTest.java
java -cp /tmp/o:/tmp/t com.itdesk.SelfTest ../frontend
```
`SelfTest` replaces PostgreSQL with a fake driver, so it checks routing, permissions, validation and the
generated SQL, but **not** that the SQL runs on a real PostgreSQL. Do a first run with `seed.sql` and try each role.

## Who can do what (enforced by the server)
| Role | Read | Create / edit | Delete |
|---|---|---|---|
| Administrator | everything | everything, settings, users | yes |
| IT Technician | everything except users | everything except users, employees, departments, locations, categories | no |
| Employee | own tickets only | create tickets (requester/status set by the server) | no |

## Bugs fixed from the original file
- **Create New Ticket** reopened the blank form right after saving.
- **Dates used UTC**, so "today" was wrong from 00:00 to 07:00 in Cambodia (UTC+7). Now local time.
- **CSV export** dropped columns missing from the first row, and cells starting with `=` could run as spreadsheet formulas.
- **XSS risk**: record ids were pasted unescaped into `onclick` attributes. Buttons now use `data-` attributes.
- **Employees** could pick any requester and set any status. Now hidden in the form and enforced on the server.
- **Cloud sync** could overwrite the shared audit log after a failed read; ids made in the browser could collide between users. Now ids come from database sequences and the audit log is written by the server.
- Ticket form accepted an empty description; dark/light toggle did nothing when the OS was already dark; "added ..." log lines had no id; restoring a partial backup crashed; ticket numbers were hard-coded to 2026; a deleted ticket left its comments behind.
- Supabase was replaced by the Java + PostgreSQL layer; `movements.from/to` became `from_party/to_party` (reserved SQL words).

## Known limits
- Tables link by **name** (department, owner, asset "IT-0001 Name"), as in the original screens. Renaming a department does not update assets.
- Email notifications and the SLA hours are stored but nothing sends or uses them yet.
- One database connection per request (fine for a small team; add a pool such as HikariCP if it grows).
- Use HTTPS in front of the server before exposing it beyond one computer: passwords travel in the login request.
