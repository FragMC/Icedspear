package com.stufy.fragmc.icedspear.api;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.api.events.*;
import com.stufy.fragmc.icedspear.managers.*;
import com.stufy.fragmc.icedspear.models.LeaderboardEntry;
import com.stufy.fragmc.icedspear.models.MapInstance;
import com.stufy.fragmc.icedspear.models.Party;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Main API class for IcedSpear
 * Access via Bukkit's ServicesManager or IcedSpear plugin instance
 *
 * Example usage:
 * <pre>
 * IcedSpearAPI api = Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class).getProvider();
 * </pre>
 */
public class IcedSpearAPI {
    private final IcedSpear plugin;
    private final MapManager mapManager;
    private final PartyManager partyManager;
    private final FriendManager friendManager;
    private final SchematicManager schematicManager;
    private final ConfigManager configManager;
    private final LeaderboardManager leaderboardManager;

    public IcedSpearAPI(IcedSpear plugin, MapManager mapManager, PartyManager partyManager,
                        FriendManager friendManager, SchematicManager schematicManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.partyManager = partyManager;
        this.friendManager = friendManager;
        this.schematicManager = schematicManager;
        this.configManager = configManager;
        this.leaderboardManager = plugin.getLeaderboardManager();
    }

    /**
     * Get the IcedSpear plugin instance
     * @return IcedSpear plugin
     */
    public Plugin getPlugin() {
        return plugin;
    }

    // ===== MAP API =====

    public Set<String> getAvailableMaps() {
        return schematicManager.getAllMaps().keySet();
    }

    public Map<String, MapInstance> getActiveMapInstances() {
        return mapManager.getActiveInstances();
    }

    public MapInstance getMapInstance(String instanceId) {
        return mapManager.getInstance(instanceId);
    }

    public MapInstance getPlayerMapInstance(Player player) {
        String instanceId = mapManager.getPlayerInstance(player.getUniqueId());
        return instanceId != null ? mapManager.getInstance(instanceId) : null;
    }

    public String createPublicMap(String mapName) {
        return mapManager.createPublicMap(mapName);
    }

    public String createPrivateMap(String mapName) {
        return mapManager.createPrivateMap(mapName);
    }

    public boolean joinMap(Player player, String instanceId) {
        return mapManager.joinMap(player, instanceId);
    }

    public void leaveMap(Player player) {
        mapManager.leaveMap(player);
    }

    public boolean canPlayerJoinMap(Player player, String mapName) {
        return configManager.canPlayerJoinMap(player, mapName);
    }

    // ===== PARTY API =====

    public Party getParty(String partyCode) {
        return partyManager.getParty(partyCode);
    }

    public Party getPlayerParty(Player player) {
        String code = partyManager.getPlayerParty(player.getUniqueId());
        return code != null ? partyManager.getParty(code) : null;
    }

    public String createParty(Player leader) {
        return partyManager.createParty(leader);
    }

    public boolean joinParty(Player player, String partyCode) {
        return partyManager.joinParty(player, partyCode);
    }

    public void leaveParty(Player player) {
        partyManager.leaveParty(player);
    }

    public void sendPartyMessage(String partyCode, String message) {
        Party party = partyManager.getParty(partyCode);
        if (party != null) {
            partyManager.broadcastToParty(party, message);
        }
    }

    // ===== FRIEND API =====

    public Set<UUID> getFriends(Player player) {
        return friendManager.getFriends(player.getUniqueId());
    }

    public boolean areFriends(Player player1, Player player2) {
        return friendManager.areFriends(player1.getUniqueId(), player2.getUniqueId());
    }

    public boolean sendFriendRequest(Player sender, Player target) {
        return friendManager.sendFriendRequest(sender, target);
    }

    public Set<UUID> getPendingFriendRequests(Player player) {
        return friendManager.getPendingRequests(player.getUniqueId());
    }

    // ===== SCHEMATIC API =====

    public String getSchematicForMap(String mapName) {
        return schematicManager.getSchematicForMap(mapName);
    }

    public Map<String, String> getAllMapSchematics() {
        return schematicManager.getAllMaps();
    }

    // ===== CONFIG API =====

    public int getMaxPlayers() {
        return configManager.getMaxPlayers();
    }

    public long getCleanupDelay() {
        return configManager.getCleanupDelay();
    }

    public boolean isMapGloballyBlocked(String mapName) {
        return configManager.isMapGloballyBlocked(mapName);
    }

    // ===== LEADERBOARD API =====

    /**
     * Get the top N entries for a map, sorted best time first.
     *
     * <pre>
     * List&lt;LeaderboardEntry&gt; top = api.getTopTimes("Snowfall", 10);
     * for (LeaderboardEntry entry : top) {
     *     int rank = top.indexOf(entry) + 1;
     *     player.sendMessage("#" + rank + " " + entry.getPlayerName() + " - " + entry.getFormattedTime());
     * }
     * </pre>
     *
     * @param mapName The map name
     * @param limit   Max number of entries to return
     * @return Ordered list of entries (best time first), empty list if none recorded
     */
    public List<LeaderboardEntry> getTopTimes(String mapName, int limit) {
        return leaderboardManager.getTopTimes(mapName, limit);
    }

    /**
     * Get the entry at a specific rank position (1-based).
     * Useful when you only need e.g. the #1 record holder.
     *
     * @param mapName The map name
     * @param rank    1-based rank (1 = best time)
     * @return LeaderboardEntry at that rank, or null if rank is out of range
     */
    public LeaderboardEntry getEntryAtRank(String mapName, int rank) {
        List<LeaderboardEntry> entries = leaderboardManager.getTopTimes(mapName, rank);
        if (entries.size() < rank) return null;
        return entries.get(rank - 1);
    }

    /**
     * Get a specific player's best time on a map.
     *
     * @param mapName  The map name
     * @param playerId The player's UUID
     * @return Best time in milliseconds, or -1 if they have no recorded time
     */
    public long getPlayerTime(String mapName, UUID playerId) {
        return leaderboardManager.getPlayerTime(mapName, playerId);
    }

    /**
     * Get a specific player's best time on a map.
     *
     * @param mapName The map name
     * @param player  The player
     * @return Best time in milliseconds, or -1 if they have no recorded time
     */
    public long getPlayerTime(String mapName, Player player) {
        return leaderboardManager.getPlayerTime(mapName, player.getUniqueId());
    }

    /**
     * Get a player's rank on a map's leaderboard.
     *
     * @param mapName  The map name
     * @param playerId The player's UUID
     * @return 1-based rank (1 = best), or -1 if they have no recorded time
     */
    public int getPlayerRank(String mapName, UUID playerId) {
        return leaderboardManager.getPlayerRank(mapName, playerId);
    }

    /**
     * Get a player's rank on a map's leaderboard.
     *
     * @param mapName The map name
     * @param player  The player
     * @return 1-based rank (1 = best), or -1 if they have no recorded time
     */
    public int getPlayerRank(String mapName, Player player) {
        return leaderboardManager.getPlayerRank(mapName, player.getUniqueId());
    }

    /**
     * Check whether a player has a recorded time on a map.
     *
     * @param mapName  The map name
     * @param playerId The player's UUID
     * @return true if they have at least one recorded time
     */
    public boolean hasTime(String mapName, UUID playerId) {
        return leaderboardManager.getPlayerTime(mapName, playerId) != -1;
    }

    /**
     * Check whether a player has a recorded time on a map.
     *
     * @param mapName The map name
     * @param player  The player
     * @return true if they have at least one recorded time
     */
    public boolean hasTime(String mapName, Player player) {
        return hasTime(mapName, player.getUniqueId());
    }

    /**
     * Get the total number of players with recorded times on a map.
     *
     * @param mapName The map name
     * @return Number of entries on the leaderboard, 0 if none
     */
    public int getLeaderboardSize(String mapName) {
        return leaderboardManager.getTopTimes(mapName, Integer.MAX_VALUE).size();
    }

    /**
     * Get every map's leaderboard data at once.
     * Returns a copy — edits to the returned map won't affect internal state.
     *
     * @return Map of map name → list of entries (sorted best time first)
     */
    public Map<String, List<LeaderboardEntry>> getAllLeaderboards() {
        return leaderboardManager.getAllLeaderboards();
    }
}