package com.stufy.fragmc.icedspear.api;

import com.google.gson.JsonObject;
import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.models.MapInstance;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IcedSpearAPI {
    private static IcedSpear plugin;
    private static IcedSpearAPI instance;

    private IcedSpearAPI(IcedSpear plugin) {
        IcedSpearAPI.plugin = plugin;
    }

    public static void initialize(IcedSpear plugin) {
        if (instance == null) {
            instance = new IcedSpearAPI(plugin);
        }
    }

    public static IcedSpearAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("IcedSpearAPI is not initialized!");
        }
        return instance;
    }

    /**
     * Get a set of all available map names (loaded from map-data-url).
     * @return Set of map names.
     */
    public Set<String> getAvailableMaps() {
        return plugin.getSchematicManager().getAvailableMaps();
    }

    /**
     * Get the raw JSON data for a specific map.
     * @param mapName The name of the map.
     * @return JsonObject containing map data, or null if not found.
     */
    public JsonObject getMapData(String mapName) {
        return plugin.getSchematicManager().getMapData(mapName);
    }

    /**
     * Get all active map instances.
     * @return Map of instance ID to MapInstance.
     */
    public Map<String, MapInstance> getActiveMapInstances() {
        return plugin.getMapManager().getActiveInstances();
    }

    /**
     * Get the map instance ID that a player is currently in.
     * @param playerUuid The player's UUID.
     * @return The instance ID, or null if not in a map.
     */
    public String getPlayerMapId(UUID playerUuid) {
        return plugin.getMapManager().getPlayerMapId(playerUuid);
    }
    
    /**
     * Create a public map instance.
     * @param mapName The name of the map to create.
     * @return The instance ID of the created map.
     */
    public String createPublicMap(String mapName) {
        return plugin.getMapManager().createPublicMap(mapName);
    }
    
    /**
     * Create a private map instance.
     * @param mapName The name of the map to create.
     * @return The instance ID of the created map.
     */
    public String createPrivateMap(String mapName) {
        return plugin.getMapManager().createPrivateMap(mapName);
    }
}
