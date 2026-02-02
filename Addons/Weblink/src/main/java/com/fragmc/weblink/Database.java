package com.fragmc.weblink;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * SQLite persistence for linked accounts.
 *
 * Schema:
 *   linked_accounts (uuid TEXT UNIQUE NOT NULL, accid TEXT NOT NULL)
 *
 * uuid  = Minecraft UUID (dashes, lowercase) — unique, one MC account links to one website account.
 * accid = SHA-256 hex of the website ACCID — NOT unique, multiple UUIDs can share the same accid.
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
            Class.forName("org.sqlite.JDBC");

            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA busy_timeout=5000;");
            }

            migrateIfNeeded();
            createTable();
            log.info("SQLite database opened: " + dbFile.getName());

        } catch (ClassNotFoundException e) {
            log.severe("SQLite JDBC driver class not found.");
            throw new RuntimeException(e);
        } catch (SQLException e) {
            log.severe("Failed to open SQLite database: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * If the old schema exists (uuid as PRIMARY KEY with an implicit unique index named
     * "sqlite_autoindex_linked_accounts_1"), drop and recreate so the new schema takes over.
     * This is a one-time migration — after it runs the old table is gone.
     */
    private void migrateIfNeeded() throws SQLException {
        try (Statement st = conn.createStatement()) {
            // Check if the old primary-key index exists — that's the fingerprint of the old schema.
            ResultSet rs = st.executeQuery(
                    "SELECT name FROM sqlite_master WHERE type='index' " +
                            "AND tbl_name='linked_accounts' " +
                            "AND name='sqlite_autoindex_linked_accounts_1'");
            if (rs.next()) {
                log.info("Migrating linked_accounts to multi-account schema...");
                // Read existing data before dropping
                Map<String, String> existing = new HashMap<>();
                try (Statement st2 = conn.createStatement();
                     ResultSet rs2 = st2.executeQuery("SELECT uuid, accid FROM linked_accounts")) {
                    while (rs2.next()) {
                        existing.put(rs2.getString("uuid"), rs2.getString("accid"));
                    }
                }

                st.execute("DROP TABLE linked_accounts;");
                createTable(); // creates the new schema

                // Re-insert preserved rows
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO linked_accounts (uuid, accid) VALUES (?, ?)")) {
                    for (var entry : existing.entrySet()) {
                        ps.setString(1, entry.getKey());
                        ps.setString(2, entry.getValue());
                        ps.executeUpdate();
                    }
                }
                log.info("Migration complete. " + existing.size() + " row(s) preserved.");
            }
        }
    }

    private void createTable() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS linked_accounts (
                        uuid  TEXT NOT NULL UNIQUE,
                        accid TEXT NOT NULL
                    );
                    """);
            // Index on accid for fast reverse lookups (one accid → many uuids)
            st.execute("""
                    CREATE INDEX IF NOT EXISTS idx_accid ON linked_accounts (accid);
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

    /**
     * Reverse lookup: hashed ACCID → all linked MC UUIDs.
     * Returns an empty list when nothing is linked.
     */
    public synchronized List<UUID> getUuidsByAccid(String hashedAccid) {
        List<UUID> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM linked_accounts WHERE accid = ?")) {
            ps.setString(1, hashedAccid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        results.add(UUID.fromString(rs.getString("uuid")));
                    } catch (IllegalArgumentException e) {
                        log.warning("Skipping bad UUID in reverse lookup: " + rs.getString("uuid"));
                    }
                }
            }
        } catch (SQLException e) {
            log.severe("DB getUuidsByAccid failed: " + e.getMessage());
        }
        return results;
    }

    /** Get all UUIDs linked to a hashed ACCID. */
    public synchronized List<UUID> getAllUuidsByAccid(String hashedAccid) {
        List<UUID> uuids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid FROM linked_accounts WHERE accid = ?")) {
            ps.setString(1, hashedAccid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        uuids.add(UUID.fromString(rs.getString("uuid")));
                    } catch (IllegalArgumentException e) {
                        log.warning("Skipping invalid UUID: " + rs.getString("uuid"));
                    }
                }
            }
        } catch (SQLException e) {
            log.severe("DB getAllUuidsByAccid failed: " + e.getMessage());
        }
        return uuids;
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