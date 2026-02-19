package com.fragmc.weblink;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.stream.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.stufy.fragmc.icedspear.api.IcedSpearAPI;
import com.stufy.fragmc.icedspear.models.MapInstance;
import com.stufy.fragmc.icedspear.models.Party;
import org.bukkit.OfflinePlayer;
import java.util.Set;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.lang.reflect.Method;

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

            // Existing endpoints
            server.createContext("/webhook/check-link", new CheckLinkHandler());
            server.createContext("/webhook/verify-code", new VerifyCodeHandler());
            server.createContext("/webhook/execute-command", new ExecuteCommandHandler());
            server.createContext("/webhook/check-online", new CheckOnlineHandler());
            server.createContext("/webhook/get-command-token", new GetCommandTokenHandler());
            server.createContext("/webhook/check-admin", new CheckAdminHandler());

            // NEW IcedSpear API endpoints
            server.createContext("/webhook/get-friends", new GetPlayerFriendsHandler());
            server.createContext("/webhook/get-party", new GetPlayerPartyHandler());
            server.createContext("/webhook/get-map", new GetPlayerMapHandler());
            server.createContext("/webhook/get-available-maps", new GetAvailableMapsHandler());

            // Optional Frost API endpoints
            server.createContext("/webhook/frost/get-owned", new FrostGetOwnedCosmeticsHandler());
            server.createContext("/webhook/frost/get-equipped", new FrostGetEquippedCosmeticsHandler());
            server.createContext("/webhook/frost/owns-cosmetic", new FrostOwnsCosmeticHandler());
            server.createContext("/webhook/frost/purchase-cosmetic", new FrostPurchaseCosmeticHandler());
            server.createContext("/webhook/frost/get-store", new FrostGetStoreHandler());

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
        reader.setLenient(true);
        return new Gson().fromJson(reader, JsonObject.class);
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendResponse(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type, X-Webhook-Signature, X-Command-Token, X-Signature");

        if (status == 204) {
            ex.sendResponseHeaders(204, -1);
            ex.getResponseBody().close();
            return;
        }

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
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
            if (handlePreflight(ex))
                return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String uuidStr = json.get("uuid").getAsString();
                UUID playerUUID = UUID.fromString(uuidStr);

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

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

    /**
     * check-link: given a hashed accid, returns whether any accounts are linked
     * and the full list of linked accounts with their online status.
     *
     * Response shape:
     * {
     * "success": true,
     * "linked": true,
     * "uuid": "<first linked uuid>", // kept for backward compat
     * "username": "<first linked username>", // kept for backward compat
     * "accounts": [
     * { "uuid": "...", "username": "...", "online": true/false },
     * ...
     * ]
     * }
     */
    class CheckLinkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String accid = json.get("accid").getAsString();

                // Get ALL UUIDs linked to this accid
                List<UUID> linkedUuids = plugin.getDatabase().getAllUuidsByAccid(accid);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("linked", !linkedUuids.isEmpty());

                if (!linkedUuids.isEmpty()) {
                    // Primary account (first one)
                    UUID primaryUuid = linkedUuids.get(0);
                    resp.addProperty("uuid", primaryUuid.toString());
                    Player p = Bukkit.getPlayer(primaryUuid);
                    if (p != null) {
                        resp.addProperty("username", p.getName());
                    } else {
                        resp.addProperty("username", primaryUuid.toString());
                    }

                    // All accounts array
                    JsonArray accountsArray = new JsonArray();
                    for (UUID uuid : linkedUuids) {
                        JsonObject acc = new JsonObject();
                        acc.addProperty("uuid", uuid.toString());
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            acc.addProperty("username", player.getName());
                            acc.addProperty("online", true);
                        } else {
                            acc.addProperty("username", uuid.toString());
                            acc.addProperty("online", false);
                        }
                        accountsArray.add(acc);
                    }
                    resp.add("accounts", accountsArray);
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
            if (handlePreflight(ex))
                return;
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
            if (handlePreflight(ex))
                return;
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
            if (handlePreflight(ex))
                return;
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
                if (p != null)
                    resp.addProperty("username", p.getName());

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
            if (handlePreflight(ex))
                return;

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

                if (!plugin.getSecurityManager().verifyAccountLink(playerUUID, accid)) {
                    sendResponse(ex, 403, errorResponse("Account not linked").toString());
                    return;
                }

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

    class GetPlayerFriendsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
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

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                IcedSpearAPI api = plugin.getIcedSpearAPI();
                Set<UUID> friends = api.getFriends(player);

                JsonArray friendsArray = new JsonArray();
                for (UUID friendId : friends) {
                    OfflinePlayer friend = Bukkit.getOfflinePlayer(friendId);
                    JsonObject friendObj = new JsonObject();
                    friendObj.addProperty("uuid", friendId.toString());
                    friendObj.addProperty("username", friend.getName());
                    friendObj.addProperty("online", friend.isOnline());

                    if (friend.isOnline()) {
                        Player onlineFriend = (Player) friend;
                        MapInstance map = api.getPlayerMapInstance(onlineFriend);
                        if (map != null) {
                            friendObj.addProperty("currentMap", map.getMapName());
                            friendObj.addProperty("mapInstanceId", map.getInstanceId());
                        }
                    }

                    friendsArray.add(friendObj);
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.add("friends", friendsArray);
                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("get-friends error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class GetPlayerPartyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
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

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                IcedSpearAPI api = plugin.getIcedSpearAPI();
                Party party = api.getPlayerParty(player);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);

                if (party != null) {
                    resp.addProperty("inParty", true);
                    resp.addProperty("code", party.getCode());
                    resp.addProperty("leader", party.getLeader().toString());
                    resp.addProperty("isLeader", party.getLeader().equals(playerUUID));

                    JsonArray membersArray = new JsonArray();
                    for (UUID memberId : party.getMembers()) {
                        OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
                        JsonObject memberObj = new JsonObject();
                        memberObj.addProperty("uuid", memberId.toString());
                        memberObj.addProperty("username", member.getName());
                        memberObj.addProperty("online", member.isOnline());
                        membersArray.add(memberObj);
                    }
                    resp.add("members", membersArray);

                    if (party.getCurrentMap() != null) {
                        resp.addProperty("currentMap", party.getCurrentMap());
                    }
                } else {
                    resp.addProperty("inParty", false);
                }

                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("get-party error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class GetPlayerMapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
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

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                IcedSpearAPI api = plugin.getIcedSpearAPI();
                MapInstance map = api.getPlayerMapInstance(player);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);

                if (map != null) {
                    resp.addProperty("inMap", true);
                    resp.addProperty("mapName", map.getMapName());
                    resp.addProperty("instanceId", map.getInstanceId());
                    resp.addProperty("isPublic", map.isPublic());
                    resp.addProperty("state", map.getState().toString());
                    resp.addProperty("playerCount", map.getPlayers().size());
                    resp.addProperty("createdAt", map.getCreatedAt());
                } else {
                    resp.addProperty("inMap", false);
                }

                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("get-map error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class GetAvailableMapsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
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

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                IcedSpearAPI api = plugin.getIcedSpearAPI();
                Set<String> maps = api.getAvailableMaps();

                JsonArray mapsArray = new JsonArray();
                for (String mapName : maps) {
                    JsonObject mapObj = new JsonObject();
                    mapObj.addProperty("name", mapName);
                    mapObj.addProperty("canJoin", api.canPlayerJoinMap(player, mapName));
                    mapsArray.add(mapObj);
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.add("maps", mapsArray);
                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("get-available-maps error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    // -------------------- Frost integration (optional) --------------------

    class FrostGetOwnedCosmeticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
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

                if (!plugin.hasFrost()) {
                    sendResponse(ex, 503, errorResponse("Frost is not installed").toString());
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                Object frostApi = plugin.getFrostAPI();
                Class<?> apiClass = frostApi.getClass();
                Method method = apiClass.getMethod("getOwnedCosmetics", Player.class);
                Set<?> owned = (Set<?>) method.invoke(frostApi, player);

                JsonArray ownedArray = new JsonArray();
                for (Object id : owned) {
                    if (id != null) {
                        ownedArray.add(id.toString());
                    }
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.add("owned", ownedArray);
                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("frost get-owned error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class FrostGetEquippedCosmeticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
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

                if (!plugin.hasFrost()) {
                    sendResponse(ex, 503, errorResponse("Frost is not installed").toString());
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                Object frostApi = plugin.getFrostAPI();
                Class<?> apiClass = frostApi.getClass();
                Method method = apiClass.getMethod("getEquippedCosmetics", Player.class);
                @SuppressWarnings("unchecked")
                Map<Object, Object> equipped = (Map<Object, Object>) method.invoke(frostApi, player);

                JsonObject equippedObj = new JsonObject();
                for (Map.Entry<Object, Object> entry : equipped.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        equippedObj.addProperty(entry.getKey().toString(), entry.getValue().toString());
                    }
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.add("equipped", equippedObj);
                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("frost get-equipped error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class FrostOwnsCosmeticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();
                String cosmeticId = json.get("cosmeticId").getAsString();
                UUID playerUUID = UUID.fromString(uuidStr);

                if (!plugin.getSecurityManager().verifyAccountLink(playerUUID, accid)) {
                    sendResponse(ex, 403, errorResponse("Account not linked").toString());
                    return;
                }

                if (!plugin.hasFrost()) {
                    sendResponse(ex, 503, errorResponse("Frost is not installed").toString());
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                Object frostApi = plugin.getFrostAPI();
                Class<?> apiClass = frostApi.getClass();
                Method method = apiClass.getMethod("playerOwnsCosmetic", Player.class, String.class);
                boolean owns = (boolean) method.invoke(frostApi, player, cosmeticId);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.addProperty("owns", owns);
                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("frost owns-cosmetic error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class FrostPurchaseCosmeticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();
                String cosmeticId = json.get("cosmeticId").getAsString();
                UUID playerUUID = UUID.fromString(uuidStr);

                if (!plugin.getSecurityManager().verifyAccountLink(playerUUID, accid)) {
                    sendResponse(ex, 403, errorResponse("Account not linked").toString());
                    return;
                }

                if (!plugin.hasFrost()) {
                    sendResponse(ex, 503, errorResponse("Frost is not installed").toString());
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                Object frostApi = plugin.getFrostAPI();
                Class<?> apiClass = frostApi.getClass();
                Method method = apiClass.getMethod("purchaseCosmetic", Player.class, String.class);
                boolean purchased = (boolean) method.invoke(frostApi, player, cosmeticId);

                JsonObject resp = new JsonObject();
                resp.addProperty("success", purchased);
                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("frost purchase-cosmetic error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }

    class FrostGetStoreHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (handlePreflight(ex))
                return;
            if (!"POST".equals(ex.getRequestMethod())) {
                sendResponse(ex, 405, errorResponse("Method not allowed").toString());
                return;
            }

            try {
                JsonObject json = lenientParse(readBody(ex));
                String uuidStr = json.get("uuid").getAsString();
                String accid = json.get("accid").getAsString();
                String categoryId = json.has("categoryId") ? json.get("categoryId").getAsString() : null;
                UUID playerUUID = UUID.fromString(uuidStr);

                if (!plugin.getSecurityManager().verifyAccountLink(playerUUID, accid)) {
                    sendResponse(ex, 403, errorResponse("Account not linked").toString());
                    return;
                }

                if (!plugin.hasFrost()) {
                    sendResponse(ex, 503, errorResponse("Frost is not installed").toString());
                    return;
                }

                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null) {
                    sendResponse(ex, 400, errorResponse("Player not online").toString());
                    return;
                }

                Object frostApi = plugin.getFrostAPI();
                Class<?> apiClass = frostApi.getClass();

                List<?> store;
                if (categoryId != null && !categoryId.isEmpty()) {
                    Method m = apiClass.getMethod("getStoreCosmeticsInCategory", Player.class, String.class);
                    store = (List<?>) m.invoke(frostApi, player, categoryId);
                } else {
                    Method m = apiClass.getMethod("getStoreCosmetics", Player.class);
                    store = (List<?>) m.invoke(frostApi, player);
                }

                JsonArray items = new JsonArray();
                for (Object cosmetic : store) {
                    if (cosmetic == null)
                        continue;
                    JsonObject obj = new JsonObject();
                    Class<?> c = cosmetic.getClass();

                    try {
                        Method getId = c.getMethod("getId");
                        Object id = getId.invoke(cosmetic);
                        if (id != null)
                            obj.addProperty("id", id.toString());
                    } catch (NoSuchMethodException ignored) {
                    }

                    try {
                        Method getDisplayName = c.getMethod("getDisplayName");
                        Object name = getDisplayName.invoke(cosmetic);
                        if (name != null)
                            obj.addProperty("displayName", name.toString());
                    } catch (NoSuchMethodException ignored) {
                    }

                    try {
                        Method getDescription = c.getMethod("getDescription");
                        Object desc = getDescription.invoke(cosmetic);
                        if (desc != null)
                            obj.addProperty("description", desc.toString());
                    } catch (NoSuchMethodException ignored) {
                    }

                    try {
                        Method getCategoryId = c.getMethod("getCategoryId");
                        Object cat = getCategoryId.invoke(cosmetic);
                        if (cat != null)
                            obj.addProperty("categoryId", cat.toString());
                    } catch (NoSuchMethodException ignored) {
                    }

                    try {
                        Method getPrice = c.getMethod("getPrice");
                        Object price = getPrice.invoke(cosmetic);
                        if (price instanceof Number n) {
                            obj.addProperty("price", n.doubleValue());
                        }
                    } catch (NoSuchMethodException ignored) {
                    }

                    items.add(obj);
                }

                JsonObject resp = new JsonObject();
                resp.addProperty("success", true);
                resp.add("store", items);
                sendResponse(ex, 200, resp.toString());

            } catch (Exception e) {
                plugin.getLogger().warning("frost get-store error: " + e.getMessage());
                sendResponse(ex, 400, errorResponse("Invalid request: " + e.getMessage()).toString());
            }
        }
    }
}
