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

    @SuppressWarnings({"deprecation", "removal"})
    public void applyWorldSettings(World world) {
        // Apply time settings
        long time = plugin.getConfig().getLong("world-settings.time", 6000);
        world.setTime(time);

        // Apply spawn settings - deprecated but still present in 26.2.build.123
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

    @SuppressWarnings({"deprecation", "removal", "unchecked"})
    public void applyGameRules(World world) {
        if (!plugin.getConfig().contains("world-gamerules")) {
            // Apply default game rules if not in config - use Registry for 26.2 compatibility
            try {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            } catch (Exception e) {
                // Fallback via string lookup
                applyGameRuleByName(world, "doDaylightCycle", false);
                applyGameRuleByName(world, "doWeatherCycle", false);
                applyGameRuleByName(world, "doMobSpawning", false);
            }
            return;
        }

        var gameRulesSection = plugin.getConfig().getConfigurationSection("world-gamerules");
        if (gameRulesSection == null) return;

        for (String key : gameRulesSection.getKeys(false)) {
            try {
                GameRule<?> gameRule = getGameRuleByName(key);
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

    @SuppressWarnings({"deprecation", "removal", "unchecked"})
    private GameRule<?> getGameRuleByName(String key) {
        // Try deprecated getByName first (Paper 1.21)
        try {
            GameRule<?> r = GameRule.getByName(key);
            if (r != null) return r;
        } catch (Exception ignored) {}
        // Try Registry for Paper 26.2: minecraft:do_daylight_cycle etc.
        try {
            String normalized = key.toLowerCase().replace("_", "");
            // Map common keys to namespaced keys
            // Use Registry if available (Paper 26.2)
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Object gameRuleRegistry = registryClass.getField("GAME_RULE").get(null);
            // Try via NamespacedKey
            Class<?> nsKeyClass = Class.forName("org.bukkit.NamespacedKey");
            java.lang.reflect.Method minecraft = nsKeyClass.getMethod("minecraft", String.class);
            // Convert upper underscore to lower underscore
            String lower = key.toLowerCase();
            if (!lower.contains("_") && key.equals(key.toUpperCase())) {
                // already upper underscore, convert to lower underscore
                lower = key.toLowerCase();
            } else if (!key.contains("_")) {
                // camelCase like doDaylightCycle -> do_daylight_cycle
                lower = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
            }
            Object nsKey = minecraft.invoke(null, lower);
            java.lang.reflect.Method get = gameRuleRegistry.getClass().getMethod("get", nsKeyClass);
            Object result = get.invoke(gameRuleRegistry, nsKey);
            if (result instanceof GameRule) return (GameRule<?>) result;
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void applyGameRuleByName(World world, String key, boolean value) {
        try {
            GameRule<?> rule = getGameRuleByName(key);
            if (rule != null && rule.getType() == Boolean.class) {
                world.setGameRule((GameRule<Boolean>) rule, value);
            }
        } catch (Exception ignored) {}
    }
}