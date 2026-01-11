package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public long getNoJoinCleanupDelay() {
        return plugin.getConfig().getLong("no-join-cleanup-delay-seconds", 60);
    }

    public GameMode getDefaultGameMode() {
        String mode = plugin.getConfig().getString("default-gamemode", "ADVENTURE");
        try {
            return GameMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid gamemode in config: " + mode + ", using ADVENTURE");
            return GameMode.ADVENTURE;
        }
    }

    public List<String> getOnJoinCommands() {
        return plugin.getConfig().getStringList("on-join-commands");
    }

    public void executeOnJoinCommands(Player player) {
        List<String> commands = getOnJoinCommands();
        for (String command : commands) {
            String processedCommand = command.replace("%player%", player.getName());
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), processedCommand);
        }
    }

    public void applyWorldSettings(World world) {
        // Apply time settings
        long time = plugin.getConfig().getLong("world-settings.time", 6000);
        world.setTime(time);

        // Apply spawn settings
        boolean keepSpawn = plugin.getConfig().getBoolean("world-settings.keep-spawn-in-memory", false);
        world.setKeepSpawnInMemory(keepSpawn);

        // Apply auto-save
        boolean autoSave = plugin.getConfig().getBoolean("world-settings.auto-save", false);
        world.setAutoSave(autoSave);

        // Disable mob spawning
        world.setSpawnFlags(false, false);

        // Apply game rules
        applyGameRules(world);
    }

    public void applyGameRules(World world) {
        if (!plugin.getConfig().contains("world-gamerules")) {
            // Apply default game rules if not in config
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            return;
        }

        var gameRulesSection = plugin.getConfig().getConfigurationSection("world-gamerules");
        if (gameRulesSection == null) return;

        for (String key : gameRulesSection.getKeys(false)) {
            try {
                GameRule<?> gameRule = GameRule.getByName(key);
                if (gameRule == null) {
                    plugin.getLogger().warning("Unknown game rule: " + key);
                    continue;
                }

                Object value = gameRulesSection.get(key);

                if (gameRule.getType() == Boolean.class) {
                    world.setGameRule((GameRule<Boolean>) gameRule, (Boolean) value);
                } else if (gameRule.getType() == Integer.class) {
                    world.setGameRule((GameRule<Integer>) gameRule, ((Number) value).intValue());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to apply game rule " + key + ": " + e.getMessage());
            }
        }
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