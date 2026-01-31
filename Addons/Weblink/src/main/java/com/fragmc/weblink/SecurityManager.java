package com.fragmc.weblink;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SecurityManager {
    private final WebLinkAddon plugin;
    private final String webhookSecret;
    private final Map<String, Long> requestNonces;
    private static final long NONCE_EXPIRY_MS = TimeUnit.MINUTES.toMillis(5);

    public SecurityManager(WebLinkAddon plugin) {
        this.plugin = plugin;
        this.webhookSecret = plugin.getConfig().getString("webhook-secret", "CHANGE_ME_IN_CONFIG");
        this.requestNonces = new ConcurrentHashMap<>();

        if (webhookSecret.equals("CHANGE_ME_IN_CONFIG")) {
            plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            plugin.getLogger().severe("CRITICAL: Change webhook-secret in config!");
            plugin.getLogger().severe("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    /**
     * Verify webhook request signature using HMAC-SHA256
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }

        try {
            String expectedSignature = "sha256=" + generateHMAC(payload);
            return MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to verify signature: " + e.getMessage());
            return false;
        }
    }

    /**
     * Generate HMAC-SHA256 signature
     */
    private String generateHMAC(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(secretKeySpec);
        byte[] hmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmac);
    }

    /**
     * Verify that the request is from a linked account
     */
    public boolean verifyAccountLink(UUID playerUUID, String accid) {
        String linkedAccid = plugin.getLinkManager().getLinkedAccount(playerUUID);
        if (linkedAccid == null) {
            return false;
        }

        // Hash the ACCID for comparison (both should be hashed)
        return linkedAccid.equals(accid);
    }

    /**
     * Prevent replay attacks using nonces
     */
    public boolean verifyNonce(String nonce) {
        if (nonce == null || nonce.isEmpty()) {
            return false;
        }

        // Check if nonce already used
        if (requestNonces.containsKey(nonce)) {
            return false;
        }

        // Store nonce
        requestNonces.put(nonce, System.currentTimeMillis());

        // Cleanup old nonces
        cleanupOldNonces();

        return true;
    }

    /**
     * Hash ACCID using SHA-256 (for storage)
     */
    public String hashAccid(String accid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(accid.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to hash ACCID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Validate command execution request
     */
    public boolean validateCommandRequest(UUID playerUUID, String accid, String command) {
        // Verify player is online
        if (plugin.getServer().getPlayer(playerUUID) == null) {
            return false;
        }

        // Verify account link
        if (!verifyAccountLink(playerUUID, accid)) {
            return false;
        }

        // Verify command is whitelisted
        if (!isCommandWhitelisted(command)) {
            return false;
        }

        return true;
    }

    /**
     * Check if command is whitelisted
     */
    private boolean isCommandWhitelisted(String command) {
        String[] parts = command.toLowerCase().split(" ");
        if (parts.length == 0) {
            return false;
        }

        String baseCommand = parts[0];

        // Whitelist of allowed commands
        switch (baseCommand) {
            case "map":
                if (parts.length >= 2) {
                    String subCmd = parts[1];
                    return subCmd.equals("public") || subCmd.equals("private") ||
                            subCmd.equals("join") || subCmd.equals("leave");
                }
                return false;

            case "party":
                if (parts.length >= 2) {
                    String subCmd = parts[1];
                    return subCmd.equals("create") || subCmd.equals("join") ||
                            subCmd.equals("kick") || subCmd.equals("leave") ||
                            subCmd.equals("list") || subCmd.equals("map");
                }
                return false;

            default:
                return false;
        }
    }

    private void cleanupOldNonces() {
        long now = System.currentTimeMillis();
        requestNonces.entrySet().removeIf(entry ->
                now - entry.getValue() > NONCE_EXPIRY_MS
        );
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}