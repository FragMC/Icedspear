package com.stufy.fragmc.icedspear.api;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.api.events.*;
import com.stufy.fragmc.icedspear.managers.*;
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

    public IcedSpearAPI(IcedSpear plugin, MapManager mapManager, PartyManager partyManager,
                        FriendManager friendManager, SchematicManager schematicManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.partyManager = partyManager;
        this.friendManager = friendManager;
        this.schematicManager = schematicManager;
        this.configManager = configManager;
    }

    /**
     * Get the IcedSpear plugin instance
     * @return IcedSpear plugin
     */
    public Plugin getPlugin() {
        return plugin;
    }

    // ===== MAP API =====

    /**
     * Get all available map names
     * @return Set of map names
     */
    public Set<String> getAvailableMaps() {
        return schematicManager.getAllMaps().keySet();
    }

    /**
     * Get all active map instances
     * @return Map of instance ID to MapInstance
     */
    public Map<String, MapInstance> getActiveMapInstances() {
        return mapManager.getActiveInstances();
    }

    /**
     * Get a specific map instance by ID
     * @param instanceId The instance ID
     * @return MapInstance or null if not found
     */
    public MapInstance getMapInstance(String instanceId) {
        return mapManager.getInstance(instanceId);
    }

    /**
     * Get the map instance a player is currently in
     * @param player The player
     * @return MapInstance or null if player is not in any map
     */
    public MapInstance getPlayerMapInstance(Player player) {
        String instanceId = mapManager.getPlayerInstance(player.getUniqueId());
        return instanceId != null ? mapManager.getInstance(instanceId) : null;
    }

    /**
     * Create a public map instance
     * @param mapName The name of the map
     * @return The instance ID
     */
    public String createPublicMap(String mapName) {
        return mapManager.createPublicMap(mapName);
    }

    /**
     * Create a private map instance
     * @param mapName The name of the map
     * @return The instance ID
     */
    public String createPrivateMap(String mapName) {
        return mapManager.createPrivateMap(mapName);
    }

    /**
     * Make a player join a map instance
     * @param player The player
     * @param instanceId The instance ID
     * @return true if successful
     */
    public boolean joinMap(Player player, String instanceId) {
        return mapManager.joinMap(player, instanceId);
    }

    /**
     * Make a player leave their current map
     * @param player The player
     */
    public void leaveMap(Player player) {
        mapManager.leaveMap(player);
    }

    /**
     * Check if a player can join a specific map
     * @param player The player
     * @param mapName The map name
     * @return true if player has permission and is not blocked
     */
    public boolean canPlayerJoinMap(Player player, String mapName) {
        return configManager.canPlayerJoinMap(player, mapName);
    }

    // ===== PARTY API =====

    /**
     * Get a party by its code
     * @param partyCode The party code
     * @return Party or null if not found
     */
    public Party getParty(String partyCode) {
        return partyManager.getParty(partyCode);
    }

    /**
     * Get the party a player is in
     * @param player The player
     * @return Party or null if player is not in a party
     */
    public Party getPlayerParty(Player player) {
        String code = partyManager.getPlayerParty(player.getUniqueId());
        return code != null ? partyManager.getParty(code) : null;
    }

    /**
     * Create a party with a player as leader
     * @param leader The party leader
     * @return The party code or null if player is already in a party
     */
    public String createParty(Player leader) {
        return partyManager.createParty(leader);
    }

    /**
     * Make a player join a party
     * @param player The player
     * @param partyCode The party code
     * @return true if successful
     */
    public boolean joinParty(Player player, String partyCode) {
        return partyManager.joinParty(player, partyCode);
    }

    /**
     * Make a player leave their party
     * @param player The player
     */
    public void leaveParty(Player player) {
        partyManager.leaveParty(player);
    }

    /**
     * Send a message to all party members
     * @param partyCode The party code
     * @param message The message to send
     */
    public void sendPartyMessage(String partyCode, String message) {
        Party party = partyManager.getParty(partyCode);
        if (party != null) {
            partyManager.broadcastToParty(party, message);
        }
    }

    // ===== FRIEND API =====

    /**
     * Get a player's friends
     * @param player The player
     * @return Set of friend UUIDs
     */
    public Set<UUID> getFriends(Player player) {
        return friendManager.getFriends(player.getUniqueId());
    }

    /**
     * Check if two players are friends
     * @param player1 First player
     * @param player2 Second player
     * @return true if they are friends
     */
    public boolean areFriends(Player player1, Player player2) {
        return friendManager.areFriends(player1.getUniqueId(), player2.getUniqueId());
    }

    /**
     * Send a friend request
     * @param sender The player sending the request
     * @param target The player receiving the request
     * @return true if request was sent successfully
     */
    public boolean sendFriendRequest(Player sender, Player target) {
        return friendManager.sendFriendRequest(sender, target);
    }

    /**
     * Get pending friend requests for a player
     * @param player The player
     * @return Set of requester UUIDs
     */
    public Set<UUID> getPendingFriendRequests(Player player) {
        return friendManager.getPendingRequests(player.getUniqueId());
    }

    // ===== SCHEMATIC API =====

    /**
     * Get the schematic name for a map
     * @param mapName The map name
     * @return The schematic name or null if not found
     */
    public String getSchematicForMap(String mapName) {
        return schematicManager.getSchematicForMap(mapName);
    }

    /**
     * Get all registered maps and their schematics
     * @return Map of map name to schematic name
     */
    public Map<String, String> getAllMapSchematics() {
        return schematicManager.getAllMaps();
    }

    // ===== CONFIG API =====

    /**
     * Get the maximum players per map
     * @return Max players
     */
    public int getMaxPlayers() {
        return configManager.getMaxPlayers();
    }

    /**
     * Get the cleanup delay in seconds
     * @return Cleanup delay
     */
    public long getCleanupDelay() {
        return configManager.getCleanupDelay();
    }

    /**
     * Check if a map is globally blocked
     * @param mapName The map name
     * @return true if blocked
     */
    public boolean isMapGloballyBlocked(String mapName) {
        return configManager.isMapGloballyBlocked(mapName);
    }
}