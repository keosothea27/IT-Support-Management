package com.itdesk;

import com.itdesk.Entities.Ent;
import com.sun.net.httpserver.HttpServer;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Dependency-free test. Run it with:
 *   java -cp target/classes:target/test-classes com.itdesk.SelfTest ../frontend
 * It starts the real HTTP server but swaps PostgreSQL for a scripted fake JDBC driver, so it checks routing,
 * permissions, validation and the SQL that is generated. It does NOT prove the SQL runs on a real PostgreSQL.
 */
public final class SelfTest {
    static int failed = 0, passed = 0;
    static final List<String> SQL = new ArrayList<>();
    static final List<List<Object>> PARAMS = new ArrayList<>();
    static final String PW = "correct-horse-battery";
    static String pwHash;
    static final Map<String, String[]> SESSIONS = new HashMap<>();   // sha256(token) -> id, name, email, role

    static void check(boolean ok, String what) {
        if (ok) passed++; else failed++;
        System.out.println((ok ? "ok   : " : "FAIL : ") + what);
    }

    // ================= fake JDBC =================
    static final class Rows {
        final List<String> cols; final List<List<String>> data;
        Rows(List<String> cols, List<List<String>> data) { this.cols = cols; this.data = data; }
        static Rows of(String cols, String... row) { return new Rows(List.of(cols.split(",")), List.of(List.of(row))); }
    }

    static Rows respond(String sql, List<Object> p) {
        if (sql.contains("FROM sessions s JOIN app_users")) {
            String[] u = SESSIONS.get(String.valueOf(p.get(0)));
            return u == null ? new Rows(List.of(), List.of()) : Rows.of("id,name,email,role", u);
        }
        if (sql.contains("FROM app_users WHERE lower(email)")) {
            if (!"admin@example.com".equalsIgnoreCase(String.valueOf(p.get(0)))) return new Rows(List.of(), List.of());
            return Rows.of("id,name,email,role,status,password_hash", "USR-01", "Admin", "admin@example.com", "Administrator", "Active", pwHash);
        }
        if (sql.contains("nextval")) return Rows.of("n", "7");
        if (sql.contains("setting_key")) return new Rows(List.of("k", "v"), List.of(List.of("co", "Acme IT")));
        if (sql.contains("to_char(logged_at")) return Rows.of("l", "2026-11-03 10:00 - Administrator (Admin): added assets IT-0007");
        var m = java.util.regex.Pattern.compile("FROM \"(\\w+)\"").matcher(sql);
        if (sql.startsWith("SELECT \"id\"") && m.find()) {
            for (Ent e : Entities.ALL.values()) if (e.table.equals(m.group(1))) {
                List<String> row = new ArrayList<>(List.of("ID-7"));
                for (int i = 0; i < e.cols.size(); i++) row.add("v");
                return new Rows(List.of(), List.of(row));
            }
        }
        return new Rows(List.of(), List.of());
    }

    static Object stmt(String preset, boolean prepared) {
        List<Object> params = new ArrayList<>();
        return Proxy.newProxyInstance(SelfTest.class.getClassLoader(), new Class<?>[]{prepared ? PreparedStatement.class : Statement.class}, (px, m, a) -> {
            String n = m.getName();
            switch (n) {
                case "setString", "setInt": while (params.size() < (int) a[0]) params.add(null); params.set((int) a[0] - 1, a[1]); return null;
                case "setNull": while (params.size() < (int) a[0]) params.add(null); params.set((int) a[0] - 1, null); return null;
                case "executeUpdate": { String s = prepared ? preset : (String) a[0]; SQL.add(s); PARAMS.add(params);
                    if (s.startsWith("INSERT INTO sessions")) SESSIONS.put(String.valueOf(params.get(0)), new String[]{"USR-01", "Admin", "admin@example.com", "Administrator"}); return s.startsWith("DELETE") && params.contains("MISSING") ? 0 : 1; }
                case "executeQuery": { String s = prepared ? preset : (String) a[0]; SQL.add(s); PARAMS.add(params); return rs(respond(s, params)); }
                case "close": return null;
                default: throw new UnsupportedOperationException(n);
            }
        });
    }

    static ResultSet rs(Rows r) {
        int[] i = {-1};
        return (ResultSet) Proxy.newProxyInstance(SelfTest.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (px, m, a) -> {
            switch (m.getName()) {
                case "next": return ++i[0] < r.data.size();
                case "getString": { int k = a[0] instanceof Integer x ? x - 1 : r.cols.indexOf((String) a[0]); return r.data.get(i[0]).get(k); }
                case "getLong": return Long.parseLong(r.data.get(i[0]).get((int) a[0] - 1));
                case "close": return null;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
    }

    static Connection conn() {
        return (Connection) Proxy.newProxyInstance(SelfTest.class.getClassLoader(), new Class<?>[]{Connection.class}, (px, m, a) -> {
            switch (m.getName()) {
                case "prepareStatement": return stmt((String) a[0], true);
                case "createStatement": return stmt(null, false);
                case "setAutoCommit", "commit", "rollback", "close": return null;
                case "isClosed": return false;
                default: throw new UnsupportedOperationException(m.getName());
            }
        });
    }

    // ================= HTTP helpers =================
    static HttpClient http = HttpClient.newHttpClient();
    static String base;

    static HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    static boolean sqlHas(String frag) { return SQL.stream().anyMatch(s -> s.contains(frag)); }

    static void installFakeDb() throws Exception {
        DriverManager.registerDriver(new Driver() {
            public Connection connect(String url, Properties i) { return url.startsWith("jdbc:postgresql:") ? conn() : null; }
            public boolean acceptsURL(String url) { return url.startsWith("jdbc:postgresql:"); }
            public DriverPropertyInfo[] getPropertyInfo(String u, Properties i) { return new DriverPropertyInfo[0]; }
            public int getMajorVersion() { return 1; }
            public int getMinorVersion() { return 0; }
            public boolean jdbcCompliant() { return false; }
            public Logger getParentLogger() { return Logger.getAnonymousLogger(); }
        });
    }

    public static void main(String[] args) throws Exception {
        Path web = Path.of(args.length > 0 ? args[0] : "../frontend");
        if (args.length > 1 && args[1].equals("serve")) {   // keeps the fake-database server running for browser-side tests
            pwHash = Auth.hashPassword(PW);
            installFakeDb();
            SESSIONS.put(Auth.sha256("t-emp"), new String[]{"USR-03", "Eve", "e@x", "Employee"});
            App.start(18080, "127.0.0.1", web);
            System.out.println("serving on 18080");
            Thread.sleep(Long.MAX_VALUE);
        }

        // ---------- pure units ----------
        Object j = Json.parse("{\"a\":[1,2.5,true,null,\"x\\n\\u00e9\"],\"b\":{\"c\":\"q\\\"\"}}");
        check(Json.write(j).equals("{\"a\":[1,2.5,true,null,\"x\\n\u00e9\"],\"b\":{\"c\":\"q\\\"\"}}"), "Json round-trips numbers, escapes and nesting");
        for (String bad : new String[]{"{", "{\"a\":}", "[1,]", "{\"a\":1} x", "\"\\q\"", "[".repeat(40)}) {
            try { Json.parse(bad); check(false, "Json rejects " + bad); } catch (ApiError e) { check(e.status == 400, "Json rejects " + bad.substring(0, Math.min(8, bad.length()))); }
        }
        pwHash = Auth.hashPassword(PW);
        check(Auth.verifyPassword(PW, pwHash) && !Auth.verifyPassword("wrong-password", pwHash) && !Auth.verifyPassword("x", null), "password hash verifies only the right password");
        check(!pwHash.contains(PW) && !pwHash.equals(Auth.hashPassword(PW)), "password hash is salted");
        Ent assets = Entities.ALL.get("assets");
        check(Db.normalize(assets.col("price"), " 12.50 ").equals("12.50") && Db.normalize(assets.col("price"), "") == null, "numbers are trimmed, blanks become null");
        for (String[] t : new String[][]{{"purchase", "31/12/2026"}, {"price", "abc"}, {"price", "1e12"}}) {
            try { Db.normalize(assets.col(t[0]), t[1]); check(false, "reject " + t[0] + "=" + t[1]); } catch (ApiError e) { check(true, "rejects bad " + t[0] + "=" + t[1]); }
        }
        User admin = new User("USR-01", "Admin", "a@x", "Administrator"), tech = new User("USR-02", "Tina", "t@x", "IT Technician"), emp = new User("USR-03", "Eve", "e@x", "Employee");
        Ent tickets = Entities.ALL.get("tickets"), users = Entities.ALL.get("users"), cats = Entities.ALL.get("categories");
        check(App.canRead(admin, users) && !App.canRead(tech, users) && !App.canRead(emp, assets) && App.canRead(emp, tickets), "read matrix");
        check(App.canWrite(admin, users) && !App.canWrite(tech, users) && !App.canWrite(tech, cats) && App.canWrite(tech, assets) && !App.canWrite(emp, assets) && App.canWrite(emp, tickets), "write matrix");
        Map<String, Object> in = new LinkedHashMap<>(Map.of("description", "x", "requester", "Someone Else", "status", "Closed", "tech", "T", "priority", "High", "hours", "99"));
        App.lockEmployeeTicket(emp, in);
        check("Eve".equals(in.get("requester")) && "Open".equals(in.get("status")) && !in.containsKey("tech") && !in.containsKey("hours") && "High".equals(in.get("priority")), "employee ticket is locked down: " + in);

        // ---------- fake DB + real server ----------
        installFakeDb();
        SESSIONS.put(Auth.sha256("t-admin"), new String[]{"USR-01", "Admin", "admin@example.com", "Administrator"});
        SESSIONS.put(Auth.sha256("t-tech"), new String[]{"USR-02", "Tina", "t@x", "IT Technician"});
        SESSIONS.put(Auth.sha256("t-emp"), new String[]{"USR-03", "Eve", "e@x", "Employee"});
        HttpServer server = App.start(0, "127.0.0.1", web);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        HttpResponse<String> r;

        r = call("GET", "/api/health", null, null);
        check(r.statusCode() == 200 && r.body().equals("{\"ok\":true}"), "GET /api/health");
        r = call("GET", "/", null, null);
        check(r.statusCode() == 200 && r.body().contains("<title>IT Management System</title>") && r.headers().firstValue("X-Frame-Options").isPresent(), "serves index.html with security headers");
        r = call("GET", "/js/data.js", null, null);
        check(r.statusCode() == 200 && r.headers().firstValue("Content-Type").orElse("").startsWith("text/javascript"), "serves JS with the right content type");
        check(call("GET", "/%2e%2e/backend/pom.xml", null, null).statusCode() == 404, "path traversal (../) is blocked");
        check(call("GET", "//etc/passwd", null, null).statusCode() == 404, "absolute-path trick is blocked");
        check(call("POST", "/index.html", null, "{}").statusCode() == 405, "static files reject POST");
        SQL.clear();
        check(call("GET", "/api/data", null, null).statusCode() == 401 && SQL.isEmpty(), "no token: 401 and no database work");
        check(call("GET", "/api/data", "garbage", null).statusCode() == 401, "unknown token: 401");

        // login
        r = call("POST", "/api/login", null, "{\"email\":\"ADMIN@example.com\",\"password\":\"" + PW + "\"}");
        check(r.statusCode() == 200 && r.body().contains("\"token\"") && r.body().contains("\"role\":\"Administrator\"") && !r.body().contains("password"), "login succeeds (email is case-insensitive), no hash leaks");
        check(sqlHas("INSERT INTO sessions") && SQL.stream().noneMatch(s -> s.contains(PW)), "session stored");
        check(call("POST", "/api/login", null, "{\"email\":\"admin@example.com\",\"password\":\"nope-nope-nope\"}").statusCode() == 401, "wrong password: 401");
        check(call("POST", "/api/login", null, "{\"email\":\"ghost@example.com\",\"password\":\"nope-nope-nope\"}").statusCode() == 401, "unknown email: 401 (same answer)");
        check(call("POST", "/api/login", null, "not json").statusCode() == 400, "malformed JSON: 400");
        int last = 0;
        for (int i = 0; i < 5; i++) last = call("POST", "/api/login", null, "{\"email\":\"admin@example.com\",\"password\":\"nope-nope-nope\"}").statusCode();
        check(last == 429, "repeated failures are throttled (429)");
        check(call("POST", "/api/login", null, "{\"email\":\"admin@example.com\",\"password\":\"" + PW + "\"}").statusCode() == 429, "locked even for the right password during the window");

        // data by role
        SQL.clear(); PARAMS.clear();
        r = call("GET", "/api/data", "t-admin", null);
        check(r.statusCode() == 200 && r.body().contains("\"users\":[") && r.body().contains("\"co\":\"Acme IT\"") && r.body().contains("\"log\":[\"2026"), "admin gets all tables, settings and audit log");
        r = call("GET", "/api/data", "t-tech", null);
        check(r.statusCode() == 200 && !r.body().contains("\"users\":[") && r.body().contains("\"assets\":["), "technician gets everything except users");
        SQL.clear(); PARAMS.clear();
        r = call("GET", "/api/data", "t-emp", null);
        int ti = SQL.indexOf(SQL.stream().filter(s -> s.contains("FROM \"tickets\"")).findFirst().orElse(""));
        check(r.statusCode() == 200 && !r.body().contains("\"assets\"") && r.body().contains("\"log\":[]") && ti >= 0 && SQL.get(ti).contains("\"requester\" = ?") && PARAMS.get(ti).contains("Eve"), "employee gets only their own tickets, no audit log");

        // employee limits
        check(call("POST", "/api/assets", "t-emp", "{\"name\":\"x\"}").statusCode() == 403, "employee cannot create assets");
        check(call("PUT", "/api/tickets/TKT-1", "t-emp", "{\"status\":\"Closed\"}").statusCode() == 403, "employee cannot edit tickets");
        check(call("DELETE", "/api/tickets/TKT-1", "t-emp", null).statusCode() == 403, "employee cannot delete");
        SQL.clear(); PARAMS.clear();
        r = call("POST", "/api/tickets", "t-emp", "{\"description\":\"Mouse broken\",\"requester\":\"Someone Else\",\"status\":\"Closed\",\"tech\":\"T\",\"priority\":\"High\"}");
        int ii = SQL.indexOf(SQL.stream().filter(s -> s.startsWith("INSERT INTO \"tickets\"")).findFirst().orElse(""));
        check(r.statusCode() == 201 && ii >= 0 && !SQL.get(ii).contains("\"tech\"") && PARAMS.get(ii).contains("Eve") && PARAMS.get(ii).contains("Open") && !PARAMS.get(ii).contains("Someone Else"), "employee ticket: requester and status forced by the server");
        System.out.println("       " + SQL.get(ii));

        // technician limits
        check(call("POST", "/api/users", "t-tech", "{\"name\":\"n\",\"email\":\"n@x.y\",\"role\":\"Employee\",\"password\":\"long-enough-pw\"}").statusCode() == 403, "technician cannot create users");
        check(call("PUT", "/api/settings", "t-tech", "{\"co\":\"X\"}").statusCode() == 403, "technician cannot change settings");
        check(call("DELETE", "/api/assets/IT-0001", "t-tech", null).statusCode() == 403, "technician cannot delete");
        SQL.clear(); PARAMS.clear();
        r = call("PUT", "/api/assets/IT-0001", "t-tech", "{\"status\":\"Under Repair\",\"warranty\":\"2027-01-31\",\"notes\":\"\"}");
        int ui = SQL.indexOf(SQL.stream().filter(s -> s.startsWith("UPDATE \"assets\"")).findFirst().orElse(""));
        check(r.statusCode() == 200 && ui >= 0, "technician can edit assets");
        check(SQL.get(ui).equals("UPDATE \"assets\" SET \"warranty\" = ?::date, \"status\" = ?, \"notes\" = ? WHERE \"id\" = ?"), "UPDATE only touches the sent fields: " + SQL.get(ui));
        check(sqlHas("INSERT INTO audit_log"), "every write is audited");

        // admin writes + validation
        SQL.clear(); PARAMS.clear();
        r = call("POST", "/api/assets", "t-admin", "{\"name\":\"Cisco Switch\",\"category\":\"Switch\",\"price\":\"199.9\",\"purchase\":\"2026-10-01\",\"status\":\"\",\"notes\":\"\"}");
        ii = SQL.indexOf(SQL.stream().filter(s -> s.startsWith("INSERT INTO \"assets\"")).findFirst().orElse(""));
        check(r.statusCode() == 201 && SQL.get(ii).equals("INSERT INTO \"assets\" (\"id\", \"name\", \"category\", \"purchase\", \"price\") VALUES (?, ?, ?, ?::date, ?::numeric)") && PARAMS.get(ii).get(0).equals("IT-0007"), "INSERT skips blank fields (DB defaults apply), id from sequence: " + PARAMS.get(ii));
        check(call("POST", "/api/assets", "t-admin", "{}").statusCode() == 400, "missing required field: 400");
        check(call("POST", "/api/assets", "t-admin", "{\"name\":\"x\",\"price\":\"abc\"}").statusCode() == 400, "invalid number: 400");
        check(call("POST", "/api/tickets", "t-admin", "{\"requester\":\"A\",\"description\":\" \"}").statusCode() == 400, "blank description: 400");
        check(call("PUT", "/api/assets/IT-0001", "t-admin", "{\"name\":\"\"}").statusCode() == 400, "cannot blank a required field on edit");
        check(call("POST", "/api/nothing", "t-admin", "{}").statusCode() == 404, "unknown resource: 404");
        check(call("DELETE", "/api/assets/MISSING", "t-admin", null).statusCode() == 404, "deleting a missing record: 404");
        check(call("DELETE", "/api/assets/IT-0001", "t-admin", null).statusCode() == 200, "admin can delete");
        check(call("PUT", "/api/settings", "t-admin", "{\"co\":\"New Co\",\"n1\":0,\"n2\":true}").statusCode() == 200 && sqlHas("INSERT INTO settings"), "admin can save settings");
        check(call("PUT", "/api/settings", "t-admin", "{\"co\":\"\"}").statusCode() == 400, "empty company name: 400");

        // users
        check(call("POST", "/api/users", "t-admin", "{\"name\":\"N\",\"email\":\"n@x.y\",\"role\":\"Employee\"}").statusCode() == 400, "new user needs a password");
        check(call("POST", "/api/users", "t-admin", "{\"name\":\"N\",\"email\":\"n@x.y\",\"role\":\"Employee\",\"password\":\"short\"}").statusCode() == 400, "short password rejected");
        check(call("POST", "/api/users", "t-admin", "{\"name\":\"N\",\"email\":\"n@x.y\",\"role\":\"Root\",\"password\":\"long-enough-pw\"}").statusCode() == 400, "unknown role rejected");
        check(call("POST", "/api/users", "t-admin", "{\"name\":\"N\",\"email\":\"not-an-email\",\"role\":\"Employee\",\"password\":\"long-enough-pw\"}").statusCode() == 400, "bad email rejected");
        SQL.clear(); PARAMS.clear();
        r = call("POST", "/api/users", "t-admin", "{\"name\":\"N\",\"email\":\"n@x.y\",\"role\":\"Employee\",\"password\":\"long-enough-pw\"}");
        check(r.statusCode() == 201 && !r.body().contains("long-enough-pw") && !r.body().contains("password") && sqlHas("UPDATE app_users SET password_hash"), "user created, password hashed, never returned");
        check(PARAMS.stream().flatMap(List::stream).noneMatch(x -> "long-enough-pw".equals(x)), "plain password is never sent to the database");
        check(call("PUT", "/api/users/USR-01", "t-admin", "{\"role\":\"Employee\"}").statusCode() == 400, "admin cannot demote themselves");
        check(call("PUT", "/api/users/USR-01", "t-admin", "{\"status\":\"Disabled\"}").statusCode() == 400, "admin cannot disable themselves");
        check(call("DELETE", "/api/users/USR-01", "t-admin", null).statusCode() == 400, "admin cannot delete themselves");
        SQL.clear();
        check(call("PUT", "/api/users/USR-05", "t-admin", "{\"role\":\"IT Technician\"}").statusCode() == 200 && sqlHas("DELETE FROM sessions WHERE user_id"), "changing someone's role ends their sessions");
        r = call("GET", "/api/me", "t-tech", null);
        check(r.statusCode() == 200 && r.body().contains("\"name\":\"Tina\""), "GET /api/me");
        check(call("POST", "/api/logout", "t-tech", null).statusCode() == 200 && sqlHas("DELETE FROM sessions WHERE token_hash"), "logout deletes the session");

        server.stop(0);
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }
}
