package com.fragmc.weblink;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LinkManager {

    private final WebLinkAddon plugin;
    private final Database     db;

    // In-memory caches — mirrors the DB for fast lookups.
    // Every mutation writes to SQLite first; cache is updated only on success.
    private final Map<UUID,   String> linkedAccounts; // MC UUID  → hashed ACCID
    private final Map<String, UUID>   reverseLinks;   // hashed ACCID → MC UUID

    // Pending (unverified) codes — ephemeral, never persisted.
    private final Map<String, LinkCode> pendingLinks;

    private final ScheduledExecutorService scheduler;

    private static final String CHARACTERS    = "0123456789";
    private static final int   CODE_LENGTH   = 6;
    private static final long  CODE_EXPIRY_MS = 120_000; // 2 minutes

    // ------------------------------------------------------------------

    public LinkManager(WebLinkAddon plugin, Database db) {
        this.plugin        = plugin;
        this.db            = db;
        this.pendingLinks  = new ConcurrentHashMap<>();
        this.linkedAccounts = new ConcurrentHashMap<>();
        this.reverseLinks   = new ConcurrentHashMap<>();
        this.scheduler      = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(this::cleanupExpiredCodes, 30, 30, TimeUnit.SECONDS);

        // Warm caches from the database
        loadLinkedAccounts();
    }

    // ------------------------------------------------------------------
    // Code generation
    // ------------------------------------------------------------------

    public String generateLinkCode(Player player) {
        // Discard any previous pending code for this player
        pendingLinks.values().removeIf(c -> c.playerUUID.equals(player.getUniqueId()));

        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(rng.nextInt(CHARACTERS.length())));
        }

        String code = sb.toString();
        pendingLinks.put(code, new LinkCode(player.getUniqueId(), player.getName()));
        plugin.getLogger().info("Generated link code " + code + " for " + player.getName());
        return code;
    }

    // ------------------------------------------------------------------
    // Code verification & linking
    // ------------------------------------------------------------------

    /**
     * Verify a code and persist the link.
     *
     * @param code        The 6-digit code the player received in-game.
     * @param hashedAccid The SHA-256 hex of the website ACCID
     *                    (hashing is done by the caller — see WebhookServer).
     * @return true when the link was successfully created.
     */
    public boolean verifyAndLinkAccount(String code, String hashedAccid) {
        LinkCode linkCode = pendingLinks.remove(code); // consume immediately — single-use

        if (linkCode == null) {
            plugin.getLogger().warning("Verify attempted with unknown/already-used code: " + code);
            return false;
        }

        if (linkCode.isExpired()) {
            plugin.getLogger().warning("Verify attempted with expired code for " + linkCode.playerName);
            return false;
        }

        UUID playerUUID = linkCode.playerUUID;

        // Persist first
        db.save(playerUUID, hashedAccid);

        // Then update caches
        linkedAccounts.put(playerUUID, hashedAccid);
        reverseLinks.put(hashedAccid, playerUUID);

        plugin.getLogger().info("Linked hashed-ACCID to " + linkCode.playerName + " (" + playerUUID + ")");
        return true;
    }

    // ------------------------------------------------------------------
    // Queries (read from cache)
    // ------------------------------------------------------------------

    public boolean isLinked(UUID playerUUID) {
        return linkedAccounts.containsKey(playerUUID);
    }

    /** Returns the hashed ACCID stored for this MC UUID, or null. */
    public String getLinkedAccount(UUID playerUUID) {
        return linkedAccounts.get(playerUUID);
    }

    /** Reverse lookup: hashed ACCID → MC UUID, or null. */
    public UUID getLinkedPlayer(String hashedAccid) {
        return reverseLinks.get(hashedAccid);
    }

    // ------------------------------------------------------------------
    // Unlink
    // ------------------------------------------------------------------

    public boolean unlinkAccount(UUID playerUUID) {
        String accid = linkedAccounts.remove(playerUUID);
        if (accid == null) return false;

        reverseLinks.remove(accid);
        db.delete(playerUUID);

        plugin.getLogger().info("Unlinked account for " + playerUUID);
        return true;
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private void cleanupExpiredCodes() {
        pendingLinks.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private void loadLinkedAccounts() {
        Map<UUID, String> all = db.loadAll();
        for (Map.Entry<UUID, String> entry : all.entrySet()) {
            linkedAccounts.put(entry.getKey(),   entry.getValue());
            reverseLinks.put(entry.getValue(), entry.getKey());
        }
        plugin.getLogger().info("Loaded " + all.size() + " linked account(s) from SQLite.");
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void cleanup() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // LinkCode — ephemeral, never written to disk
    // ------------------------------------------------------------------

    private static class LinkCode {
        final UUID   playerUUID;
        final String playerName;
        final long   createdAt = System.currentTimeMillis();

        LinkCode(UUID playerUUID, String playerName) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CODE_EXPIRY_MS;
        }
    }
}