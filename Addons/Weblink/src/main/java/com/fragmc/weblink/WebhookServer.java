package com.fragmc.weblink;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class WebhookServer {

    private final WebLinkAddon plugin;
    private HttpServer server;
    private final int port;
    private final String corsOrigin;

    // --- command token storage ---
    private final Map<String, Long> commandTokens = new ConcurrentHashMap<>();
    private final long TOKEN_LIFETIME_MS = 30_000; // 30 seconds
    private final SecureRandom random = new SecureRandom();

    public WebhookServer(WebLinkAddon plugin) {
        this.plugin = plugin;
        this.port = plugin.getConfig().getInt("webhook-port", 25531);
        this.corsOrigin = plugin.getConfig().getString("cors-origin", "*");
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/webhook/check-link", new CheckLinkHandler());
            server.createContext("/webhook/verify-code", new VerifyCodeHandler());
            server.createContext("/webhook/execute-command", new ExecuteCommandHandler());
            server.createContext("/webhook/check-online", new CheckOnlineHandler());
            server.createContext("/webhook/get-command-token", new GetCommandTokenHandler());
            server.createContext("/webhook/check-admin", new CheckAdminHandler());

            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            plugin.getLogger().info("Webhook server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start webhook server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Webhook server stopped.");
        }
    }

    // -------------------- Helpers --------------------

    private static JsonObject lenientParse(String raw) {
        JsonReader reader = new JsonReader(new StringReader(raw));
        reader.setLenient(true); // fixed from Strictness
        return new Gson().fromJson(reader, JsonObject.class);
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendResponse(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-Webhook-Signature, X-Command-Token, X-Signature");

        ex.sendResponseHeaders(status, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private boolean handlePreflight(HttpExchange ex) throws IOException {
        if ("OPTIONS".equals(ex.getRequestMethod())) {
            sendResponse(ex, 204, "");
            return true;
        }
        return false;
    }

    private static JsonObject successResponse(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("success", true);
        o.addProperty("message", message);
        return o;
    }

    private static JsonObject errorResponse(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("success", false);
        o.addProperty("message", message);
        return o;
    }

    // -------------------- Token system --------------------

    /** Generate a new short-lived command token for a player UUID. */
    private String generateToken(UUID uuid) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        commandTokens.put(token, System.currentTimeMillis() + TOKEN_LIFETIME_MS);
        return token;
    }

    /** Verify token and consume it. */
    private boolean verifyToken(String token) {
        Long expiry = commandTokens.remove(token);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    // -------------------- Handlers --------------------

    class GetCommandTokenHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String uuidStr = json.get("uuid").getAsString();
                UUID playerUUID = UUID.fromString(uuidStr);

                // Validate player is online & linked
                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                // Generate token
                String token = generateToken(playerUUID);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("token", token);
                resp.addProperty("expires_ms", TOKEN_LIFETIME_MS);

                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("get-command-token error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class CheckLinkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String accid = json.get("accid").getAsString();
                UUID linked = plugin.getLinkManager().getLinkedPlayer(accid);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("linked", linked != null);
                if (linked != null) {
                    resp.addProperty("uuid", linked.toString());
                    Player p = Bukkit.getPlayer(linked);
                    if (p != null) resp.addProperty("username", p.getName());
                }

                sendResponse(ex, 200, resp.toString());
            } catch (Exception e) {
                plugin.getLogger().warning("check-link error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class VerifyCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String code = json.get("code").getAsString();
                String accid = json.get("accid").getAsString();

                String hashed = plugin.getSecurityManager().hashAccid(accid);
                boolean ok = plugin.getLinkManager().verifyAndLinkAccount(code, hashed);

                if (ok) {
                    sendResponse(ex, 200, successResponse("Account linked successfully").toString());
                } else {
                    sendResponse(ex, 400, errorResponse("Invalid or expired code").toString());
                }

            } catch (Exception e) {
                plugin.getLogger().warning("verify-code error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class ExecuteCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                String raw = readBody(ex);
                JsonObject json = lenientParse(raw);

                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();
                String command = json.get("command").getAsString();
                String token = ex.getRequestHeaders().getFirst("X-Command-Token");

                if (token == null || !verifyToken(token)) {
                    sendResponse(ex, 401, errorResponse("Invalid or expired command token").toString());
                    return;
                }

                UUID playerUUID = UUID.fromString(uuidStr);

                if (!plugin.getSecurityManager().validateCommandRequest(playerUUID, accid, command)) {
                    sendResponse(ex, 403, errorResponse("Unauthorized or invalid command").toString());
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
                sendResponse(ex, 200, successResponse("Command executed").toString());

            } catch (Exception e) {
                plugin.getLogger().warning("execute-command error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class CheckOnlineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();
                UUID playerUUID = UUID.fromString(uuidStr);

                if (!plugin.getSecurityManager().verifyAccountLink(playerUUID, accid)) {
                    sendResponse(ex, 403, errorResponse("Account not linked").toString());
                    return;
                }

                Player p = Bukkit.getPlayer(playerUUID);
                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("online", p != null);
                if (p != null) resp.addProperty("username", p.getName());

                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("check-online error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }
    class CheckAdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex)) return;

            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                String raw = readBody(ex);
                JsonObject json = lenientParse(raw);

                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();

                UUID playerUUID = UUID.fromString(uuidStr);

                // verify link (plugin should store hashed accid)
                if (!plugin.getSecurityManager().verifyAccountLink(playerUUID, accid)) {
                    sendResponse(ex, 403, errorResponse("Account not linked").toString());
                    return;
                }

                // check permission
                Player player = Bukkit.getPlayer(playerUUID);
                boolean isAdmin = false;
                String username = null;

                if (player != null) {
                    isAdmin = player.hasPermission("weblink.admin");
                    username = player.getName();
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("admin", isAdmin);
                if (username != null) {
                    resp.addProperty("username", username);
                }
                sendResponse(ex, 200, resp.toString());
            } catch (Exception e) {
                plugin.getLogger().warning("check-admin error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }
}
