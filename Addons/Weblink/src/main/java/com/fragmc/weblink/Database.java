package com.fragmc.weblink;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQLite persistence for linked accounts.
 *
 * Schema:
 *   linked_accounts (uuid TEXT PRIMARY KEY, accid TEXT NOT NULL)
 *
 * uuid  = Minecraft UUID as a string (with dashes, lowercase)
 * accid = SHA-256 hex string of the website ACCID
 *
 * WAL mode is enabled so concurrent reads from the webhook thread pool
 * never block each other.  Every public method is synchronized because
 * we reuse a single Connection — the critical sections are all
 * single-row point operations so the lock is never held for long.
 */
public class Database {

    private final Logger log;
    private Connection  conn;

    public Database(WebLinkAddon plugin) {
        this.log = plugin.getLogger();
        File dbFile = new File(plugin.getDataFolder(), "linked_accounts.db");

        try {
            // The shade plugin relocates org.sqlite → com.fragmc.weblink.shade.sqlite.
            // We must load the driver by its RELOCATED class name so the JVM finds it.
            Class.forName("com.fragmc.weblink.shade.sqlite.JDBC");

            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA busy_timeout=5000;");
            }

            createTable();
            log.info("SQLite database opened: " + dbFile.getName());

        } catch (ClassNotFoundException e) {
            log.severe("SQLite JDBC driver class not found. The shade relocation may have failed.");
            throw new RuntimeException(e);
        } catch (SQLException e) {
            log.severe("Failed to open SQLite database: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void createTable() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS linked_accounts (
                        uuid  TEXT NOT NULL PRIMARY KEY,
                        accid TEXT NOT NULL
                    );
                    """);
        }
    }

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    /** Insert or replace a row. */
    public synchronized void save(UUID uuid, String hashedAccid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR REPLACE INTO linked_accounts (uuid, accid) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, hashedAccid);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("DB save failed for " + uuid + ": " + e.getMessage());
        }
    }

    /** Delete a row.  Returns true when something was actually removed. */
    public synchronized boolean delete(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("DB delete failed for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /** Forward lookup: MC UUID → hashed ACCID.  Returns null when missing. */
    public synchronized String getAccidByUuid(UUID uuid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT accid FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("accid") : null;
            }
        } catch (SQLException e) {
            log.severe("DB getAccidByUuid failed: " + e.getMessage());
            return null;
        }
    }

    /** Reverse lookup: hashed ACCID → MC UUID.  Returns null when missing. */
    public synchronized UUID getUuidByAccid(String hashedAccid) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM linked_accounts WHERE accid = ?")) {
            ps.setString(1, hashedAccid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString("uuid")) : null;
            }
        } catch (SQLException e) {
            log.severe("DB getUuidByAccid failed: " + e.getMessage());
            return null;
        }
    }

    /** Bulk load — called once at startup to warm the in-memory caches. */
    public synchronized Map<UUID, String> loadAll() {
        Map<UUID, String> map = new HashMap<>();
        try (Statement st   = conn.createStatement();
             ResultSet  rs   = st.executeQuery("SELECT uuid, accid FROM linked_accounts")) {
            while (rs.next()) {
                try {
                    map.put(UUID.fromString(rs.getString("uuid")), rs.getString("accid"));
                } catch (IllegalArgumentException e) {
                    log.warning("Skipping row with bad UUID: " + rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            log.severe("DB loadAll failed: " + e.getMessage());
        }
        return map;
    }

    // ------------------------------------------------------------------

    public synchronized void close() {
        if (conn == null) return;
        try {
            conn.close();
            log.info("SQLite connection closed.");
        } catch (SQLException e) {
            log.warning("Error closing SQLite connection: " + e.getMessage());
        }
    }
}