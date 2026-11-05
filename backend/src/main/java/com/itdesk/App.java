package com.itdesk;

import com.itdesk.Entities.Ent;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** HTTP server: serves the frontend folder and the JSON API under /api. */
public final class App {
    private static final int MAX_BODY = 1_000_000;
    private static final long LOCK_WINDOW_MS = 15 * 60 * 1000;
    private static final Set<String> TECH_CANNOT_EDIT = Set.of("users", "departments", "locations", "categories", "employees");
    private static final Set<String> ROLES = Set.of("Administrator", "IT Technician", "Employee");
    private static final Map<String, long[]> FAILED_LOGINS = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        try (Connection c = Db.open()) {
            Auth.ensureAdmin(c);
        }
        String bind = Db.env("BIND", "127.0.0.1");
        HttpServer s = start(Integer.parseInt(Db.env("PORT", "8080")), bind, Paths.get(Db.env("FRONTEND_DIR", "../frontend")));
        System.out.println("IT Management System is running at http://" + bind + ":" + s.getAddress().getPort());
    }

    static HttpServer start(int port, String bind, Path frontendDir) throws IOException {
        Path root = frontendDir.toAbsolutePath().normalize();
        HttpServer s = HttpServer.create(new InetSocketAddress(bind, port), 0);
        s.createContext("/", ex -> {
            try {
                String raw = ex.getRequestURI().getRawPath();
                if (raw.equals("/api") || raw.startsWith("/api/")) api(ex, raw.substring(4));
                else staticFile(ex, root, ex.getRequestURI().getPath());
            } catch (Throwable t) {
                fail(ex, t);
            } finally {
                ex.close();
            }
        });
        s.setExecutor(Executors.newFixedThreadPool(16));
        s.start();
        return s;
    }

    // ================= routing =================
    private static void api(HttpExchange ex, String rawSub) throws Exception {
        String m = ex.getRequestMethod();
        String[] seg = Arrays.stream(rawSub.split("/")).filter(x -> !x.isEmpty())
                .map(x -> URLDecoder.decode(x, StandardCharsets.UTF_8)).toArray(String[]::new);
        if (seg.length == 0) throw new ApiError(404, "Not found.");

        if (seg.length == 1 && seg[0].equals("health") && m.equals("GET")) {
            send(ex, 200, Map.of("ok", true));
            return;
        }
        if (seg.length == 1 && seg[0].equals("login") && m.equals("POST")) {
            try (Connection c = Db.open()) { login(ex, c); }
            return;
        }

        String token = bearer(ex);
        if (token == null) throw new ApiError(401, "Please sign in.");   // no database work for anonymous requests
        try (Connection c = Db.open()) {
            User u = Auth.fromToken(c, token);
            if (u == null) throw new ApiError(401, "Please sign in again.");
            String k = seg[0];
            if (seg.length == 1) {
                if (k.equals("me") && m.equals("GET")) { send(ex, 200, userMap(u)); return; }
                if (k.equals("logout") && m.equals("POST")) { Auth.endSession(c, token); send(ex, 200, Map.of("ok", true)); return; }
                if (k.equals("data") && m.equals("GET")) { data(ex, c, u); return; }
                if (k.equals("settings") && m.equals("PUT")) { settings(ex, c, u); return; }
                if (m.equals("POST")) { create(ex, c, u, k); return; }
            } else if (seg.length == 2) {
                if (m.equals("PUT") || m.equals("PATCH")) { update(ex, c, u, k, seg[1]); return; }
                if (m.equals("DELETE")) { delete(ex, c, u, k, seg[1]); return; }
            }
            throw new ApiError(404, "Not found.");
        }
    }

    // ================= permissions (enforced here, not in the browser) =================
    static boolean canRead(User u, Ent e) {
        return switch (u.role()) {
            case "Administrator" -> true;
            case "IT Technician" -> !e.key.equals("users");
            default -> e.key.equals("tickets");
        };
    }

    /** Create and edit. Employees may only create tickets (see lockEmployeeTicket). Deleting is Administrator-only. */
    static boolean canWrite(User u, Ent e) {
        return switch (u.role()) {
            case "Administrator" -> true;
            case "IT Technician" -> !TECH_CANNOT_EDIT.contains(e.key);
            default -> e.key.equals("tickets");
        };
    }

    private static ApiError forbidden() { return new ApiError(403, "You do not have permission to do that."); }

    /** An Employee only describes the problem; who, when and the status are set by the server. */
    static void lockEmployeeTicket(User u, Map<String, Object> in) {
        in.keySet().retainAll(Set.of("category", "description", "priority"));
        in.put("requester", u.name());
        in.put("status", "Open");
    }

    // ================= handlers =================
    private static void login(HttpExchange ex, Connection c) throws Exception {
        Map<String, Object> in = Json.parseObject(body(ex));
        String email = str(in.get("email")).trim(), pw = str(in.get("password"));
        if (email.isEmpty() || email.length() > 200 || pw.isEmpty() || pw.length() > Auth.MAX_PASSWORD)
            throw new ApiError(401, "Incorrect email or password.");
        String key = ex.getRemoteAddress().getAddress().getHostAddress() + "|" + email.toLowerCase();
        long now = System.currentTimeMillis();
        long[] f = FAILED_LOGINS.get(key);
        if (f != null && now - f[1] < LOCK_WINDOW_MS && f[0] >= 5)
            throw new ApiError(429, "Too many failed attempts. Please wait a few minutes.");
        User u = Auth.login(c, email, pw);
        if (u == null) {
            FAILED_LOGINS.compute(key, (k, v) -> v == null || now - v[1] >= LOCK_WINDOW_MS ? new long[]{1, now} : new long[]{v[0] + 1, v[1]});
            if (FAILED_LOGINS.size() > 10_000) FAILED_LOGINS.clear();
            throw new ApiError(401, "Incorrect email or password.");
        }
        FAILED_LOGINS.remove(key);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", Auth.createSession(c, u.id()));
        out.put("user", userMap(u));
        send(ex, 200, out);
    }

    private static void data(HttpExchange ex, Connection c, User u) throws Exception {
        boolean employee = u.role().equals("Employee");
        Map<String, Object> tables = new LinkedHashMap<>();
        for (Ent e : Entities.ALL.values()) {
            if (!canRead(u, e)) continue;
            tables.put(e.key, employee ? Db.list(c, e, "requester", u.name()) : Db.list(c, e, null, null));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tables", tables);
        out.put("cfg", Db.settings(c));
        out.put("log", employee ? List.of() : Db.recentAudit(c));
        send(ex, 200, out);
    }

    private static void create(HttpExchange ex, Connection c, User u, String key) throws Exception {
        Ent e = entity(key);
        if (!canWrite(u, e)) throw forbidden();
        Map<String, Object> in = Json.parseObject(body(ex));
        if (u.role().equals("Employee")) lockEmployeeTicket(u, in);
        requireFields(e, in, true);
        String pw = null;
        if (e.key.equals("users")) {
            checkUser(in);
            pw = password(in, true);
        }
        c.setAutoCommit(false);
        Map<String, Object> rec = Db.insert(c, e, in);
        String id = (String) rec.get("id");
        if (pw != null) Auth.setPassword(c, id, pw);
        Db.audit(c, u, "added", e.key, id);
        c.commit();
        send(ex, 201, rec);
    }

    private static void update(HttpExchange ex, Connection c, User u, String key, String id) throws Exception {
        Ent e = entity(key);
        if (!canWrite(u, e) || u.role().equals("Employee")) throw forbidden();
        Map<String, Object> in = Json.parseObject(body(ex));
        requireFields(e, in, false);
        String pw = null;
        boolean users = e.key.equals("users"), self = id.equals(u.id());
        if (users) {
            checkUser(in);
            pw = password(in, false);
            if (self && ((in.containsKey("role") && !str(in.get("role")).equals(u.role()))
                    || (in.containsKey("status") && !str(in.get("status")).equals("Active"))))
                throw new ApiError(400, "You cannot change your own role or disable your own account.");
        }
        c.setAutoCommit(false);
        boolean hasColumns = e.cols.stream().anyMatch(col -> in.containsKey(col.key));
        Map<String, Object> rec = hasColumns ? Db.update(c, e, id, in) : Db.get(c, e, id);
        if (rec == null) throw new ApiError(404, "Record not found.");
        if (pw != null) Auth.setPassword(c, id, pw);
        if (users && !self && (pw != null || in.containsKey("role") || in.containsKey("status"))) Auth.endAllSessions(c, id);
        Db.audit(c, u, "edited", e.key, id);
        c.commit();
        send(ex, 200, rec);
    }

    private static void delete(HttpExchange ex, Connection c, User u, String key, String id) throws Exception {
        Ent e = entity(key);
        if (!u.role().equals("Administrator")) throw forbidden();
        if (e.key.equals("users") && id.equals(u.id())) throw new ApiError(400, "You cannot delete your own account.");
        c.setAutoCommit(false);
        if (!Db.delete(c, e, id)) throw new ApiError(404, "Record not found.");
        Db.audit(c, u, "deleted", e.key, id);
        c.commit();
        send(ex, 200, Map.of("ok", true));
    }

    private static void settings(HttpExchange ex, Connection c, User u) throws Exception {
        if (!u.role().equals("Administrator")) throw forbidden();
        Map<String, Object> in = Json.parseObject(body(ex));
        c.setAutoCommit(false);
        if (in.containsKey("co")) {
            String co = str(in.get("co")).trim();
            if (co.isEmpty() || co.length() > 100) throw new ApiError(400, "Company name must be 1 to 100 characters.");
            Db.putSetting(c, "co", co);
        }
        for (String k : new String[]{"n1", "n2", "n3"}) {
            if (!in.containsKey(k)) continue;
            Object v = in.get(k);
            boolean on = Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0);
            Db.putSetting(c, k, on ? "1" : "0");
        }
        Db.audit(c, u, "edited", "settings", null);
        c.commit();
        send(ex, 200, Db.settings(c));
    }

    // ================= validation helpers =================
    private static Ent entity(String key) {
        Ent e = Entities.ALL.get(key);
        if (e == null) throw new ApiError(404, "Unknown resource.");
        return e;
    }

    private static String str(Object o) { return o == null ? "" : o.toString(); }

    /** On create every required field must be filled; on edit, any required field that is sent must stay filled. */
    private static void requireFields(Ent e, Map<String, Object> in, boolean creating) {
        for (String r : e.required) {
            if ((creating || in.containsKey(r)) && str(in.get(r)).isBlank())
                throw new ApiError(400, "Please fill in '" + r + "'.");
        }
    }

    private static void checkUser(Map<String, Object> in) {
        if (in.containsKey("role") && !ROLES.contains(str(in.get("role")))) throw new ApiError(400, "Unknown role.");
        if (in.containsKey("status") && !Set.of("Active", "Disabled").contains(str(in.get("status")))) throw new ApiError(400, "Status must be Active or Disabled.");
        if (in.containsKey("email") && !str(in.get("email")).matches("[^@\\s]+@[^@\\s]+")) throw new ApiError(400, "Enter a valid email address.");
    }

    private static String password(Map<String, Object> in, boolean required) {
        String p = str(in.get("password"));
        if (p.isEmpty()) {
            if (required) throw new ApiError(400, "Enter a password of at least " + Auth.MIN_PASSWORD + " characters.");
            return null;
        }
        if (p.length() < Auth.MIN_PASSWORD || p.length() > Auth.MAX_PASSWORD)
            throw new ApiError(400, "The password must be " + Auth.MIN_PASSWORD + " to " + Auth.MAX_PASSWORD + " characters.");
        return p;
    }

    private static Map<String, Object> userMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("name", u.name());
        m.put("email", u.email());
        m.put("role", u.role());
        return m;
    }

    // ================= HTTP helpers =================
    private static String bearer(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        return h != null && h.startsWith("Bearer ") && h.length() > 7 ? h.substring(7).trim() : null;
    }

    private static String body(HttpExchange ex) throws IOException {
        byte[] b = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (b.length > MAX_BODY) throw new ApiError(413, "The request is too large.");
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void securityHeaders(Headers h) {
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "no-referrer");
    }

    static void send(HttpExchange ex, int status, Object body) throws IOException {
        byte[] b = Json.write(body).getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Cache-Control", "no-store");
        securityHeaders(h);
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static void fail(HttpExchange ex, Throwable t) {
        int status = 500;
        String msg = "Something went wrong on the server.";
        if (t instanceof ApiError a) {
            status = a.status;
            msg = a.getMessage();
        } else if (t instanceof SQLException se) {
            String st = se.getSQLState() == null ? "" : se.getSQLState();
            if (st.equals("23505")) { status = 409; msg = "That value already exists."; }
            else if (st.equals("23503")) { status = 409; msg = "This record is linked to other data, or points to something that does not exist."; }
            else if (st.equals("23502")) { status = 400; msg = "A required value is missing."; }
            else if (st.equals("23514")) { status = 400; msg = "A value is not one of the allowed choices."; }
            else if (st.startsWith("22")) { status = 400; msg = "A value has the wrong format or is too large."; }
            else t.printStackTrace();
        } else {
            t.printStackTrace();
        }
        try { send(ex, status, Map.of("error", msg)); } catch (IOException ignored) { }
    }

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8", "js", "text/javascript; charset=utf-8",
            "json", "application/json; charset=utf-8", "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon", "txt", "text/plain; charset=utf-8");

    private static void staticFile(HttpExchange ex, Path root, String path) throws IOException {
        String m = ex.getRequestMethod();
        Headers h = ex.getResponseHeaders();
        securityHeaders(h);
        if (!m.equals("GET") && !m.equals("HEAD")) {
            h.set("Allow", "GET, HEAD");
            ex.sendResponseHeaders(405, -1);
            return;
        }
        Path p;
        try {
            p = root.resolve((path.equals("/") ? "/index.html" : path).substring(1)).normalize();
        } catch (InvalidPathException e) {
            ex.sendResponseHeaders(404, -1);
            return;
        }
        if (!p.startsWith(root) || !Files.isRegularFile(p)) {   // also blocks ../ and absolute-path tricks
            ex.sendResponseHeaders(404, -1);
            return;
        }
        String name = p.getFileName().toString();
        h.set("Content-Type", TYPES.getOrDefault(name.substring(name.lastIndexOf('.') + 1).toLowerCase(), "application/octet-stream"));
        h.set("Cache-Control", "no-cache");
        byte[] b = Files.readAllBytes(p);
        if (m.equals("HEAD")) {
            ex.sendResponseHeaders(200, -1);
            return;
        }
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
