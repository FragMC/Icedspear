package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import org.bukkit.entity.Player;
import java.util.List;

public class ConfigManager {
    private final IcedSpear plugin;

    public ConfigManager(IcedSpear plugin) {
        this.plugin = plugin;
    }

    public int getMaxPlayers() {
        return plugin.getConfig().getInt("max-players", 10);
    }

    public void setMaxPlayers(int maxPlayers) {
        plugin.getConfig().set("max-players", maxPlayers);
        plugin.saveConfig();
    }

    public long getCleanupDelay() {
        return plugin.getConfig().getLong("cleanup-delay-seconds", 15);
    }

    public void setCleanupDelay(long seconds) {
        plugin.getConfig().set("cleanup-delay-seconds", seconds);
        plugin.saveConfig();
    }

    public boolean canPlayerJoinMap(Player player, String mapName) {
        // Check if player has bypass permission
        if (player.hasPermission("icedspear.bypass")) {
            return true;
        }

        // Check if map is globally blocked
        if (plugin.getConfig().getBoolean("globally-blocked-maps." + mapName, false)) {
            return false;
        }

        // Check if player is individually blocked from this map
        List<String> blockedMaps = plugin.getConfig().getStringList("blocked-maps." + player.getUniqueId());

        if (blockedMaps.contains(mapName)) {
            return false;
        }

        // Check map permission
        if (!player.hasPermission("icedspear.map." + mapName) && !player.hasPermission("icedspear.map.*")) {
            return false;
        }

        return true;
    }

    public void blockMapGlobally(String mapName) {
        plugin.getConfig().set("globally-blocked-maps." + mapName, true);
        plugin.saveConfig();
    }

    public void unblockMapGlobally(String mapName) {
        plugin.getConfig().set("globally-blocked-maps." + mapName, false);
        plugin.saveConfig();
    }

    public boolean isMapGloballyBlocked(String mapName) {
        return plugin.getConfig().getBoolean("globally-blocked-maps." + mapName, false);
    }

    public void blockMapForPlayer(String playerUuid, String mapName) {
        List<String> blockedMaps = plugin.getConfig().getStringList("blocked-maps." + playerUuid);
        if (!blockedMaps.contains(mapName)) {
            blockedMaps.add(mapName);
            plugin.getConfig().set("blocked-maps." + playerUuid, blockedMaps);
            plugin.saveConfig();
        }
    }

    public void unblockMapForPlayer(String playerUuid, String mapName) {
        List<String> blockedMaps = plugin.getConfig().getStringList("blocked-maps." + playerUuid);
        blockedMaps.remove(mapName);
        plugin.getConfig().set("blocked-maps." + playerUuid, blockedMaps);
        plugin.saveConfig();
    }

    public String getMapDataUrl() {
        return plugin.getConfig().getString("map-data-url", "");
    }

    public void setMapDataUrl(String url) {
        plugin.getConfig().set("map-data-url", url);
        plugin.saveConfig();
    }
}