package com.itdesk;

import com.itdesk.Entities.Col;
import com.itdesk.Entities.Ent;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** All SQL lives here. Table and column names come only from Entities; every value is a bound parameter. */
final class Db {
    private Db() {}

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    static Connection open() throws SQLException {
        return DriverManager.getConnection(
                env("DB_URL", "jdbc:postgresql://localhost:5432/itdesk"), env("DB_USER", "itdesk"), env("DB_PASSWORD", ""));
    }

    private static String q(String identifier) { return "\"" + identifier + "\""; }

    private static String placeholder(char type) {
        return switch (type) {
            case 'D' -> "?::date";
            case 'N' -> "?::numeric";
            case 'I' -> "?::integer";
            default -> "?";
        };
    }

    /** Trims and validates one value. Returns null for "empty". */
    static String normalize(Col col, Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        if (s.isEmpty()) return null;
        if (s.length() > 4000) throw new ApiError(400, "The value for '" + col.key + "' is too long.");
        try {
            switch (col.type) {
                case 'D': return LocalDate.parse(s).toString();
                case 'N': {
                    BigDecimal n = new BigDecimal(s);
                    if (n.abs().compareTo(new BigDecimal("1e10")) >= 0) throw new ArithmeticException();
                    return n.toPlainString();
                }
                case 'I': return String.valueOf(new BigDecimal(s).intValueExact());
                default: return s;
            }
        } catch (RuntimeException e) {
            throw new ApiError(400, "'" + col.key + "' has an invalid value.");
        }
    }

    private static void bind(PreparedStatement ps, List<String> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) ps.setNull(i + 1, Types.VARCHAR);
            else ps.setString(i + 1, values.get(i));
        }
    }

    // ---------- ids ----------
    static String nextId(Connection c, Ent e) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT nextval('seq_" + e.table + "')")) {
            rs.next();
            return e.newPrefix() + String.format("%0" + e.pad + "d", rs.getLong(1));
        }
    }

    // ---------- read ----------
    private static String selectList(Ent e) {
        StringBuilder sb = new StringBuilder(q("id"));
        for (Col col : e.cols) sb.append(", ").append(q(col.key));
        return sb.toString();
    }

    private static Map<String, Object> row(ResultSet rs, Ent e) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString(1));
        for (int i = 0; i < e.cols.size(); i++) {
            String v = rs.getString(i + 2);
            m.put(e.cols.get(i).key, v == null ? "" : v);
        }
        return m;
    }

    /** whereColumn must be a constant from the calling code, never request data. */
    static List<Map<String, Object>> list(Connection c, Ent e, String whereColumn, String whereValue) throws SQLException {
        String sql = "SELECT " + selectList(e) + " FROM " + q(e.table)
                + (whereColumn == null ? "" : " WHERE " + q(whereColumn) + " = ?") + " ORDER BY " + q("id");
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (whereColumn != null) ps.setString(1, whereValue);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> out = new ArrayList<>();
                while (rs.next()) out.add(row(rs, e));
                return out;
            }
        }
    }

    static Map<String, Object> get(Connection c, Ent e, String id) throws SQLException {
        String sql = "SELECT " + selectList(e) + " FROM " + q(e.table) + " WHERE " + q("id") + " = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? row(rs, e) : null;
            }
        }
    }

    // ---------- write ----------
    /** Empty values are left out so the column's DEFAULT (for example status 'Open') applies. */
    static Map<String, Object> insert(Connection c, Ent e, Map<String, Object> in) throws SQLException {
        String id = nextId(c, e);
        List<String> names = new ArrayList<>(List.of(q("id")));
        List<String> marks = new ArrayList<>(List.of("?"));
        List<String> values = new ArrayList<>(List.of(id));
        for (Col col : e.cols) {
            String v = normalize(col, in.get(col.key));
            if (v == null) continue;
            names.add(q(col.key));
            marks.add(placeholder(col.type));
            values.add(v);
        }
        String sql = "INSERT INTO " + q(e.table) + " (" + String.join(", ", names) + ") VALUES (" + String.join(", ", marks) + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, values);
            ps.executeUpdate();
        }
        return get(c, e, id);
    }

    /** Only the fields present in the request are changed. An empty value clears the column. */
    static Map<String, Object> update(Connection c, Ent e, String id, Map<String, Object> in) throws SQLException {
        List<String> sets = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Col col : e.cols) {
            if (!in.containsKey(col.key)) continue;
            sets.add(q(col.key) + " = " + placeholder(col.type));
            values.add(normalize(col, in.get(col.key)));
        }
        if (sets.isEmpty()) throw new ApiError(400, "Nothing to update.");
        values.add(id);
        String sql = "UPDATE " + q(e.table) + " SET " + String.join(", ", sets) + " WHERE " + q("id") + " = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, values);
            if (ps.executeUpdate() == 0) throw new ApiError(404, "Record not found.");
        }
        return get(c, e, id);
    }

    static boolean delete(Connection c, Ent e, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + q(e.table) + " WHERE " + q("id") + " = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ---------- settings and audit log ----------
    static Map<String, Object> settings(Connection c) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("co", "IT Desk");
        m.put("n1", 1L);
        m.put("n2", 1L);
        m.put("n3", 1L);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT setting_key, setting_value FROM settings")) {
            while (rs.next()) {
                String k = rs.getString(1), v = rs.getString(2);
                if (k.equals("co")) m.put(k, v);
                else if (k.matches("n[123]")) m.put(k, "1".equals(v) ? 1L : 0L);
            }
        }
        return m;
    }

    static void putSetting(Connection c, String key, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO settings (setting_key, setting_value) VALUES (?, ?) "
                        + "ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    static void audit(Connection c, User u, String action, String entity, String recordId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO audit_log (actor, role, action, entity, record_id) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, u.name());
            ps.setString(2, u.role());
            ps.setString(3, action);
            ps.setString(4, entity);
            ps.setString(5, recordId);
            ps.executeUpdate();
        }
    }

    static List<String> recentAudit(Connection c) throws SQLException {
        String sql = "SELECT to_char(logged_at, 'YYYY-MM-DD HH24:MI') || ' - ' || role || ' (' || actor || '): ' || action || ' ' || entity "
                + "|| coalesce(' ' || record_id, '') FROM audit_log ORDER BY logged_at DESC, id DESC LIMIT 100";
        List<String> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }
}
