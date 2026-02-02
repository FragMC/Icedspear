package com.fragmc.weblink;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LinkManager {

    private final WebLinkAddon plugin;
    private final Database     db;

    // In-memory caches — mirrors the DB for fast lookups.
    // Every mutation writes to SQLite first; cache is updated only on success.
    private final Map<UUID,   String>       linkedAccounts; // MC UUID  → hashed ACCID
    private final Map<String, List<UUID>>   reverseLinks;   // hashed ACCID → list of MC UUIDs

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
     * Rules:
     *   - One UUID can only be linked to ONE accid (enforced here).
     *   - One accid CAN be linked to MULTIPLE UUIDs (multi-account).
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

        // Block if this UUID is already linked to a DIFFERENT accid
        String existingAccid = linkedAccounts.get(playerUUID);
        if (existingAccid != null && !existingAccid.equals(hashedAccid)) {
            plugin.getLogger().warning("UUID " + playerUUID + " is already linked to a different account. Unlink first.");
            return false;
        }

        // If already linked to the same accid, it's a no-op — still return true
        if (existingAccid != null && existingAccid.equals(hashedAccid)) {
            plugin.getLogger().info(linkCode.playerName + " is already linked to this account.");
            return true;
        }

        // Persist first
        db.save(playerUUID, hashedAccid);

        // Then update caches
        linkedAccounts.put(playerUUID, hashedAccid);
        reverseLinks.computeIfAbsent(hashedAccid, k -> new CopyOnWriteArrayList<>()).add(playerUUID);

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

    /**
     * Reverse lookup: hashed ACCID → all linked MC UUIDs.
     * Returns an empty list if nothing is linked.
     */
    public List<UUID> getLinkedPlayers(String hashedAccid) {
        List<UUID> list = reverseLinks.get(hashedAccid);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    // ------------------------------------------------------------------
    // Unlink
    // ------------------------------------------------------------------

    public boolean unlinkAccount(UUID playerUUID) {
        String accid = linkedAccounts.remove(playerUUID);
        if (accid == null) return false;

        // Remove this UUID from the reverse list; remove the list entry entirely if now empty
        List<UUID> list = reverseLinks.get(accid);
        if (list != null) {
            list.remove(playerUUID);
            if (list.isEmpty()) {
                reverseLinks.remove(accid);
            }
        }

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
            linkedAccounts.put(entry.getKey(), entry.getValue());
            reverseLinks.computeIfAbsent(entry.getValue(), k -> new CopyOnWriteArrayList<>())
                    .add(entry.getKey());
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