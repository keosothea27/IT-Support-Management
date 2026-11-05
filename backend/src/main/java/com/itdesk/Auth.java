package com.itdesk;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;
import java.util.HexFormat;

/** Passwords (PBKDF2), sessions (random bearer tokens, stored only as SHA-256 hashes) and first-run admin setup. */
final class Auth {
    private Auth() {}

    static final int MIN_PASSWORD = 10, MAX_PASSWORD = 200, SESSION_HOURS = 8;
    private static final int ITERATIONS = 210_000;
    private static final SecureRandom RNG = new SecureRandom();
    private static final byte[] DUMMY_SALT = new byte[16];

    // ---------- passwords ----------
    static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        byte[] h = pbkdf2(password.toCharArray(), salt, ITERATIONS, 32);
        Base64.Encoder b = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + b.encodeToString(salt) + "$" + b.encodeToString(h);
    }

    /** Always does the same amount of work, even for unknown users, so timing does not reveal who exists. */
    static boolean verifyPassword(String password, String stored) {
        if (stored == null) {
            pbkdf2(password.toCharArray(), DUMMY_SALT, ITERATIONS, 32);
            return false;
        }
        String[] p = stored.split("\\$");
        if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
        try {
            Base64.Decoder d = Base64.getDecoder();
            byte[] want = d.decode(p[3]);
            byte[] got = pbkdf2(password.toCharArray(), d.decode(p[2]), Integer.parseInt(p[1]), want.length);
            return MessageDigest.isEqual(want, got);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] pw, byte[] salt, int iterations, int bytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw, salt, iterations, bytes * 8);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- tokens ----------
    static String newToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- database-backed operations ----------
    /** Returns the user when email and password are right and the account is Active, otherwise null. */
    static User login(Connection c, String email, String password) throws SQLException {
        String sql = "SELECT id, name, email, role, status, password_hash FROM app_users WHERE lower(email) = lower(?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    verifyPassword(password, null);
                    return null;
                }
                boolean ok = verifyPassword(password, rs.getString("password_hash"));
                if (!ok || !"Active".equals(rs.getString("status"))) return null;
                return new User(rs.getString("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"));
            }
        }
    }

    static String createSession(Connection c, String userId) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM sessions WHERE expires_at < now()");
        }
        String token = newToken();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, now() + make_interval(hours => ?))")) {
            ps.setString(1, sha256(token));
            ps.setString(2, userId);
            ps.setInt(3, SESSION_HOURS);
            ps.executeUpdate();
        }
        return token;
    }

    static User fromToken(Connection c, String token) throws SQLException {
        String sql = "SELECT u.id, u.name, u.email, u.role FROM sessions s JOIN app_users u ON u.id = s.user_id "
                + "WHERE s.token_hash = ? AND s.expires_at > now() AND u.status = 'Active'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sha256(token));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new User(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)) : null;
            }
        }
    }

    static void endSession(Connection c, String token) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM sessions WHERE token_hash = ?")) {
            ps.setString(1, sha256(token));
            ps.executeUpdate();
        }
    }

    static void endAllSessions(Connection c, String userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM sessions WHERE user_id = ?")) {
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    static void setPassword(Connection c, String userId, String password) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE app_users SET password_hash = ? WHERE id = ?")) {
            ps.setString(1, hashPassword(password));
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    /**
     * First run: if no Administrator can sign in yet, create one from ADMIN_EMAIL / ADMIN_PASSWORD.
     * Without ADMIN_PASSWORD a random one is generated and printed once.
     */
    static void ensureAdmin(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM app_users WHERE role = 'Administrator' AND status = 'Active' AND password_hash IS NOT NULL")) {
            rs.next();
            if (rs.getLong(1) > 0) return;
        }
        String email = Db.env("ADMIN_EMAIL", "admin@example.com");
        String pw = System.getenv("ADMIN_PASSWORD");
        boolean generated = pw == null || pw.length() < MIN_PASSWORD;
        if (generated) pw = newToken().substring(0, 16);
        c.setAutoCommit(false);
        try (PreparedStatement up = c.prepareStatement(
                "UPDATE app_users SET password_hash = ?, role = 'Administrator', status = 'Active' WHERE lower(email) = lower(?)")) {
            up.setString(1, hashPassword(pw));
            up.setString(2, email);
            if (up.executeUpdate() == 0) {
                try (PreparedStatement in = c.prepareStatement(
                        "INSERT INTO app_users (id, name, email, role, status, password_hash) VALUES (?, 'Administrator', ?, 'Administrator', 'Active', ?)")) {
                    in.setString(1, Db.nextId(c, Entities.ALL.get("users")));
                    in.setString(2, email);
                    in.setString(3, hashPassword(pw));
                    in.executeUpdate();
                }
            }
        }
        c.commit();
        c.setAutoCommit(true);
        System.out.println("Created administrator account: " + email);
        if (generated) System.out.println("Initial password (shown once, change it after signing in): " + pw);
    }
}
