package com.fragmc.weblink;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    public boolean verifyAccountLink(UUID playerUUID, String accid) {
        String stored = plugin.getLinkManager().getLinkedAccount(playerUUID);
        return stored != null && stored.equals(accid);
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
        return isCommandWhitelisted(command);
    }

    // ------------------------------------------------------------------
    // Command whitelist
    // ------------------------------------------------------------------

    private boolean isCommandWhitelisted(String command) {
        String[] parts = command.toLowerCase().split(" ");
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
            default -> false;
        };
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