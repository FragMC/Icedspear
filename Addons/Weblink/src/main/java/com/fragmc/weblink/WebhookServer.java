package com.fragmc.weblink;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

public class WebhookServer {
    private final WebLinkAddon plugin;
    private HttpServer server;
    private final int port;

    public WebhookServer(WebLinkAddon plugin) {
        this.plugin = plugin;
        this.port = plugin.getConfig().getInt("webhook-port", 8080);
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/webhook/check-link", new CheckLinkHandler());
            server.createContext("/webhook/verify-code", new VerifyCodeHandler());
            server.createContext("/webhook/execute-command", new ExecuteCommandHandler());
            server.createContext("/webhook/check-online", new CheckOnlineHandler());
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
            plugin.getLogger().info("Webhook server stopped");
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private JsonObject createResponse(boolean success, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("success", success);
        response.addProperty("message", message);
        return response;
    }

    /**
     * Check if MC account is linked to ACCID
     * POST /webhook/check-link
     * Body: {"accid": "hashed_accid"}
     */
    class CheckLinkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createResponse(false, "Method not allowed").toString());
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String accid = json.get("accid").getAsString();
                UUID linkedPlayer = plugin.getLinkManager().getLinkedPlayer(accid);

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("linked", linkedPlayer != null);
                if (linkedPlayer != null) {
                    response.addProperty("uuid", linkedPlayer.toString());
                    Player player = Bukkit.getPlayer(linkedPlayer);
                    if (player != null) {
                        response.addProperty("username", player.getName());
                    }
                }

                sendResponse(exchange, 200, response.toString());
            } catch (Exception e) {
                plugin.getLogger().warning("Error in check-link: " + e.getMessage());
                sendResponse(exchange, 400, createResponse(false, "Invalid request").toString());
            }
        }
    }

    /**
     * Verify link code
     * POST /webhook/verify-code
     * Body: {"code": "123456", "accid": "hashed_accid"}
     */
    class VerifyCodeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createResponse(false, "Method not allowed").toString());
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String code = json.get("code").getAsString();
                String accid = json.get("accid").getAsString();

                // Hash the ACCID before storing
                String hashedAccid = plugin.getSecurityManager().hashAccid(accid);

                boolean success = plugin.getLinkManager().verifyAndLinkAccount(code, hashedAccid);

                if (success) {
                    sendResponse(exchange, 200, createResponse(true, "Account linked successfully").toString());
                } else {
                    sendResponse(exchange, 400, createResponse(false, "Invalid or expired code").toString());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error in verify-code: " + e.getMessage());
                sendResponse(exchange, 400, createResponse(false, "Invalid request").toString());
            }
        }
    }

    /**
     * Execute command as player
     * POST /webhook/execute-command
     * Body: {"uuid": "player-uuid", "accid": "hashed_accid", "command": "map public skyblock", "nonce": "random"}
     */
    class ExecuteCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createResponse(false, "Method not allowed").toString());
                return;
            }

            try {
                String body = readRequestBody(exchange);

                // Verify signature if provided
                String signature = exchange.getRequestHeaders().getFirst("X-Webhook-Signature");
                if (signature != null && !plugin.getSecurityManager().verifyWebhookSignature(body, signature)) {
                    sendResponse(exchange, 401, createResponse(false, "Invalid signature").toString());
                    return;
                }

                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();
                String command = json.get("command").getAsString();
                String nonce = json.has("nonce") ? json.get("nonce").getAsString() : null;

                // Verify nonce to prevent replay attacks
                if (nonce != null && !plugin.getSecurityManager().verifyNonce(nonce)) {
                    sendResponse(exchange, 400, createResponse(false, "Invalid or reused nonce").toString());
                    return;
                }

                UUID playerUUID = UUID.fromString(uuidStr);

                // Validate request
                if (!plugin.getSecurityManager().validateCommandRequest(playerUUID, accid, command)) {
                    sendResponse(exchange, 403, createResponse(false, "Unauthorized or invalid command").toString());
                    return;
                }

                // Execute command on main thread
                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(exchange, 400, createResponse(false, "Player not online").toString());
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.performCommand(command);
                });

                sendResponse(exchange, 200, createResponse(true, "Command executed").toString());

            } catch (Exception e) {
                plugin.getLogger().warning("Error in execute-command: " + e.getMessage());
                sendResponse(exchange, 400, createResponse(false, "Invalid request").toString());
            }
        }
    }

    /**
     * Check if player is online
     * POST /webhook/check-online
     * Body: {"uuid": "player-uuid", "accid": "hashed_accid"}
     */
    class CheckOnlineHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, createResponse(false, "Method not allowed").toString());
                return;
            }

            try {
                String body = readRequestBody(exchange);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();

                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();

                UUID playerUUID = UUID.fromString(uuidStr);

                // Verify account link
                if (!plugin.getSecurityManager().verifyAccountLink(playerUUID, accid)) {
                    sendResponse(exchange, 403, createResponse(false, "Account not linked").toString());
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("online", player != null);
                if (player != null) {
                    response.addProperty("username", player.getName());
                }

                sendResponse(exchange, 200, response.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("Error in check-online: " + e.getMessage());
                sendResponse(exchange, 400, createResponse(false, "Invalid request").toString());
            }
        }
    }
}