package com.fragmc.weblink;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LinkManager {
    private final WebLinkAddon plugin;
    private final Map<String, LinkCode> pendingLinks;
    private final Map<UUID, String> linkedAccounts; // MC UUID -> ACCID
    private final Map<String, UUID> reverseLinks; // ACCID -> MC UUID (primary account)
    private final ScheduledExecutorService scheduler;
    private static final String CHARACTERS = "0123456789";
    private static final int CODE_LENGTH = 6;
    private static final long CODE_EXPIRY_MS = 120000; // 2 minutes

    public LinkManager(WebLinkAddon plugin) {
        this.plugin = plugin;
        this.pendingLinks = new ConcurrentHashMap<>();
        this.linkedAccounts = new ConcurrentHashMap<>();
        this.reverseLinks = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // Cleanup expired codes every 30 seconds
        scheduler.scheduleAtFixedRate(this::cleanupExpiredCodes, 30, 30, TimeUnit.SECONDS);

        // Load linked accounts from database/config
        loadLinkedAccounts();
    }

    public String generateLinkCode(Player player) {
        // Remove any existing code for this player
        pendingLinks.values().removeIf(code -> code.getPlayerUUID().equals(player.getUniqueId()));

        // Generate new 6-digit code
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        String linkCode = code.toString();
        LinkCode codeObj = new LinkCode(player.getUniqueId(), player.getName(), linkCode);
        pendingLinks.put(linkCode, codeObj);

        plugin.getLogger().info("Generated link code " + linkCode + " for " + player.getName());
        return linkCode;
    }

    public boolean verifyAndLinkAccount(String code, String accid) {
        LinkCode linkCode = pendingLinks.get(code);
        if (linkCode == null) {
            return false;
        }

        if (linkCode.isExpired()) {
            pendingLinks.remove(code);
            return false;
        }

        // Link the account
        UUID playerUUID = linkCode.getPlayerUUID();
        linkedAccounts.put(playerUUID, accid);
        reverseLinks.put(accid, playerUUID);

        // Remove the code
        pendingLinks.remove(code);

        // Save to database/config
        saveLinkedAccount(playerUUID, accid);

        plugin.getLogger().info("Linked account " + accid + " to " + linkCode.getPlayerName());
        return true;
    }

    public boolean isLinked(UUID playerUUID) {
        return linkedAccounts.containsKey(playerUUID);
    }

    public String getLinkedAccount(UUID playerUUID) {
        return linkedAccounts.get(playerUUID);
    }

    public UUID getLinkedPlayer(String accid) {
        return reverseLinks.get(accid);
    }

    public boolean unlinkAccount(UUID playerUUID) {
        String accid = linkedAccounts.remove(playerUUID);
        if (accid != null) {
            reverseLinks.remove(accid);
            removeLinkedAccount(playerUUID);
            return true;
        }
        return false;
    }

    private void cleanupExpiredCodes() {
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    private void loadLinkedAccounts() {
        // Load from config
        if (plugin.getConfig().contains("linked-accounts")) {
            Map<String, Object> accounts = plugin.getConfig().getConfigurationSection("linked-accounts").getValues(false);
            for (Map.Entry<String, Object> entry : accounts.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    String accid = (String) entry.getValue();
                    linkedAccounts.put(uuid, accid);
                    reverseLinks.put(accid, uuid);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load linked account: " + entry.getKey());
                }
            }
        }
        plugin.getLogger().info("Loaded " + linkedAccounts.size() + " linked accounts");
    }

    private void saveLinkedAccount(UUID uuid, String accid) {
        plugin.getConfig().set("linked-accounts." + uuid.toString(), accid);
        plugin.saveConfig();
    }

    private void removeLinkedAccount(UUID uuid) {
        plugin.getConfig().set("linked-accounts." + uuid.toString(), null);
        plugin.saveConfig();
    }

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

    private static class LinkCode {
        private final UUID playerUUID;
        private final String playerName;
        private final String code;
        private final long createdAt;

        public LinkCode(UUID playerUUID, String playerName, String code) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.code = code;
            this.createdAt = System.currentTimeMillis();
        }

        public UUID getPlayerUUID() {
            return playerUUID;
        }

        public String getPlayerName() {
            return playerName;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CODE_EXPIRY_MS;
        }
    }
}