package com.fragmc.weblink;

import org.bukkit.entity.Player;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SecurityManager {

    private final WebLinkAddon       plugin;
    private final String             webhookSecret;
    private final Map<String, Long>  requestNonces;

    private static final long NONCE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(5);

    public SecurityManager(WebLinkAddon plugin) {
        this.plugin        = plugin;
        this.webhookSecret = plugin.getConfig().getString("webhook-secret", "CHANGE_ME_IN_CONFIG");
        this.requestNonces = new ConcurrentHashMap<>();

        if ("CHANGE_ME_IN_CONFIG".equals(webhookSecret)) {
            plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().severe("CRITICAL: Change webhook-secret in config!");
            plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    // ------------------------------------------------------------------
    // HMAC signature
    // ------------------------------------------------------------------

    public boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) return false;

        try {
            String expected = "sha256=" + generateHMAC(payload);
            return MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expected.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Signature verification failed: " + e.getMessage());
            return false;
        }
    }

    private String generateHMAC(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return bytesToHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------
    // Account-link verification
    // ------------------------------------------------------------------

    /**
     * Checks whether the given UUID is one of the accounts linked to the given hashed accid.
     * One accid can have multiple UUIDs linked to it, so we check membership in the list.
     */
    public boolean verifyAccountLink(UUID playerUUID, String hashedAccid) {
        List<UUID> linkedPlayers = plugin.getLinkManager().getLinkedPlayers(hashedAccid);
        return linkedPlayers.contains(playerUUID);
    }

    // ------------------------------------------------------------------
    // Nonce (replay prevention)
    // ------------------------------------------------------------------

    public boolean verifyNonce(String nonce) {
        if (nonce == null || nonce.isEmpty())    return false;
        if (requestNonces.containsKey(nonce))    return false; // already used

        requestNonces.put(nonce, System.currentTimeMillis());
        cleanupOldNonces();
        return true;
    }

    // ------------------------------------------------------------------
    // Hashing
    // ------------------------------------------------------------------

    public String hashAccid(String accid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(accid.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to hash ACCID: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Full command-request validation
    // ------------------------------------------------------------------

    public boolean validateCommandRequest(UUID playerUUID, String accid, String command) {
        if (plugin.getServer().getPlayer(playerUUID) == null) return false;
        if (!verifyAccountLink(playerUUID, accid))            return false;
        if (!isCommandWhitelisted(command))                   return false;
        if (isAdminCommand(command) && !isPlayerAdmin(playerUUID)) return false;
        return true;
    }

    // ------------------------------------------------------------------
    // Command whitelist
    // ------------------------------------------------------------------

    private boolean isCommandWhitelisted(String command) {
        String[] parts = command.toLowerCase().split(" ");
        if (parts.length == 0) return false;

        // Admin commands (single word commands)
        String[] adminCommands = {"heal", "fly", "god", "workbench", "enderchest"};
        for (String admin : adminCommands) {
            if (parts[0].equals(admin)) return true;
        }

        // Multi-word admin commands
        if (parts[0].equals("gamemode") && parts.length >= 2) {
            return parts[1].matches("creative|survival|adventure|spectator|[0-3]");
        }
        if (parts[0].equals("time") && parts.length >= 3) {
            return parts[1].equals("set") && (parts[2].equals("day") || parts[2].equals("night"));
        }
        if (parts[0].equals("weather") && parts.length >= 2) {
            return parts[1].equals("clear");
        }
        if (parts[0].equals("mvtp") && parts.length >= 2) {
            return true; // Allow any world name
        }
        if (parts[0].equals("invsee") && parts.length >= 2) {
            return true; // Allow any player name
        }

        // Regular player commands
        if (parts.length < 2) return false;

        return switch (parts[0]) {
            case "map"   -> switch (parts[1]) {
                case "public", "private", "join", "leave" -> true;
                default -> false;
            };
            case "party" -> switch (parts[1]) {
                case "create", "join", "kick", "leave", "list", "map" -> true;
                default -> false;
            };
            case "friend" -> switch (parts[1]) {
                case "add", "remove", "list", "requests" -> true;
                default -> false;
            };
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // Admin command gating
    // ------------------------------------------------------------------

    private boolean isAdminCommand(String command) {
        String first = command.toLowerCase().split(" ")[0];
        return switch (first) {
            case "gamemode", "mvtp", "heal", "fly", "god",
                 "workbench", "enderchest", "time", "weather", "invsee" -> true;
            default -> false;
        };
    }

    private boolean isPlayerAdmin(UUID playerUUID) {
        Player player = plugin.getServer().getPlayer(playerUUID);
        return player != null && player.hasPermission("weblink.admin");
    }

    // ------------------------------------------------------------------

    private void cleanupOldNonces() {
        long now = System.currentTimeMillis();
        requestNonces.entrySet().removeIf(e -> now - e.getValue() > NONCE_EXPIRY_MS);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}