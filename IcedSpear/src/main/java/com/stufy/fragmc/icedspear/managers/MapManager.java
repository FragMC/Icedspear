package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.models.MapInstance;
import com.stufy.fragmc.icedspear.models.MapState;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.generator.ChunkGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MapManager {
    private final IcedSpear plugin;
    private final SchematicManager schematicManager;
    private final ConfigManager configManager;
    private final Map<String, MapInstance> activeInstances;
    private final Map<UUID, String> playerToInstance;

    public MapManager(IcedSpear plugin, SchematicManager schematicManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.schematicManager = schematicManager;
        this.configManager = configManager;
        this.activeInstances = new ConcurrentHashMap<>();
        this.playerToInstance = new ConcurrentHashMap<>();
    }

    public Map<String, MapInstance> getActiveInstances() {
        return Collections.unmodifiableMap(activeInstances);
    }

    public String getPlayerMapId(UUID playerUuid) {
        return playerToInstance.get(playerUuid);
    }

    public String createPublicMap(String mapName) {
        String instanceId = "PUBLIC_" + mapName;

        if (activeInstances.containsKey(instanceId)) {
            return instanceId;
        }

        MapInstance instance = new MapInstance(instanceId, mapName, true);
        activeInstances.put(instanceId, instance);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            initializeMap(instance);
        });

        return instanceId;
    }

    public String createPrivateMap(String mapName) {
        String randomCode = generateRandomCode();
        String instanceId = mapName + "_" + randomCode;

        MapInstance instance = new MapInstance(instanceId, mapName, false);
        activeInstances.put(instanceId, instance);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            initializeMap(instance);
        });

        return instanceId;
    }

    public String createPartyMap(String mapName, String partyCode) {
        String instanceId = mapName + "_party-" + partyCode;

        MapInstance instance = new MapInstance(instanceId, mapName, false);
        activeInstances.put(instanceId, instance);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            initializeMap(instance);
        });

        return instanceId;
    }

    private void initializeMap(MapInstance instance) {
        instance.setState(MapState.CREATING);

        // Create void world
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Check if world folder already exists (persistence check)
            boolean worldExists = new java.io.File(Bukkit.getWorldContainer(), instance.getInstanceId()).exists();

            WorldCreator worldCreator = new WorldCreator(instance.getInstanceId());
            worldCreator.environment(World.Environment.NORMAL);
            worldCreator.type(WorldType.FLAT);
            // Use proper 1.21.x flat world generator settings for void world
            worldCreator.generator(new org.bukkit.generator.ChunkGenerator() {
                @Override
                public void generateNoise(org.bukkit.generator.WorldInfo worldInfo,
                        Random random,
                        int chunkX,
                        int chunkZ,
                        org.bukkit.generator.ChunkGenerator.ChunkData chunkData) {
                    // Generate nothing - completely void
                }

                @Override
                public void generateSurface(org.bukkit.generator.WorldInfo worldInfo,
                        Random random,
                        int chunkX,
                        int chunkZ,
                        org.bukkit.generator.ChunkGenerator.ChunkData chunkData) {
                    // Generate nothing - completely void
                }

                @Override
                public boolean shouldGenerateNoise() {
                    return false;
                }

                @Override
                public boolean shouldGenerateSurface() {
                    return false;
                }

                @Override
                public boolean shouldGenerateCaves() {
                    return false;
                }

                @Override
                public boolean shouldGenerateDecorations() {
                    return false;
                }

                @Override
                public boolean shouldGenerateMobs() {
                    return false;
                }

                @Override
                public boolean shouldGenerateStructures() {
                    return false;
                }
            });

            World world = worldCreator.createWorld();
            if (world != null) {
                world.setAutoSave(false);
                world.setSpawnFlags(false, false); // Disable mob spawning
                world.setKeepSpawnInMemory(false);

                // Set time to noon and lock it
                world.setTime(6000); // 6000 ticks = noon
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

                // Set weather to clear and lock it
                world.setStorm(false);
                world.setThundering(false);
                world.setWeatherDuration(Integer.MAX_VALUE);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);

                instance.setWorld(world);

                // Only paste schematic if the world didn't exist before
                if (!worldExists) {
                    pasteSchematic(instance);
                } else {
                    plugin.getLogger().info(
                            "Map world " + instance.getInstanceId() + " already exists. Skipping schematic paste.");

                    String schematicName = schematicManager.getSchematicForMap(instance.getMapName());
                    Location pasteLocation = new Location(world, 0, 100, 0);
                    Location spawnLocation = schematicManager.getSpawnLocation(schematicName, pasteLocation);

                    if (spawnLocation == null) {
                        spawnLocation = pasteLocation.clone().add(0, 1, 0);
                    }

                    finalizeMap(instance, spawnLocation);
                }
            }
        });
    }

    private void pasteSchematic(MapInstance instance) {
        String schematicName = schematicManager.getSchematicForMap(instance.getMapName());

        if (schematicName == null) {
            plugin.getLogger().warning("No schematic found for map: " + instance.getMapName());
            instance.setState(MapState.ERROR);
            return;
        }

        plugin.getLogger().info("Pasting schematic: " + schematicName + " for map: " + instance.getMapName());

        Location pasteLocation = new Location(instance.getWorld(), 0, 100, 0);

        Location spawnLocation = schematicManager.pasteSchematic(schematicName, pasteLocation);

        if (spawnLocation != null) {
            plugin.getLogger().info("Schematic pasted successfully.");
            finalizeMap(instance, spawnLocation);
        } else {
            plugin.getLogger().severe("Failed to paste schematic for map: " + instance.getMapName());
            instance.setState(MapState.ERROR);
        }
    }

    private void finalizeMap(MapInstance instance, Location spawnLocation) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            instance.setSpawnLocation(spawnLocation);
            plugin.getLogger().info("Map ready! Spawn set at: X=" + spawnLocation.getBlockX() +
                    " Y=" + spawnLocation.getBlockY() +
                    " Z=" + spawnLocation.getBlockZ());
            instance.setState(MapState.WAITING);

            // Teleport waiting players
            for (UUID playerId : instance.getWaitingPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    // Re-check permissions and bans just in case
                    if (!configManager.canPlayerJoinMap(player, instance.getMapName())) {
                        player.sendMessage(ChatColor.RED + "You don't have permission to join this map!");
                        continue;
                    }

                    // Check full
                    if (instance.getPlayers().size() >= configManager.getMaxPlayers()) {
                        player.sendMessage(ChatColor.RED + "Map is full!");
                        continue;
                    }

                    instance.addPlayer(playerId);
                    playerToInstance.put(playerId, instance.getInstanceId());

                    // Teleport
                    try {
                        player.teleportAsync(spawnLocation).thenAccept(success -> {
                            if (success) {
                                player.setGameMode(GameMode.ADVENTURE);
                                player.sendMessage(ChatColor.GREEN + "Map is ready! Welcome to "
                                        + instance.getMapName() + "!");
                            }
                        });
                    } catch (NoSuchMethodError e) {
                        player.teleport(spawnLocation);
                        player.setGameMode(GameMode.ADVENTURE);
                        player.sendMessage(
                                ChatColor.GREEN + "Map is ready! Welcome to " + instance.getMapName() + "!");
                    }
                }
            }
            instance.clearWaitingPlayers();

            if (!instance.getPlayers().isEmpty()) {
                instance.setState(MapState.RUNNING);
            } else {
                // No one joined (everyone quit while waiting), destroy map
                plugin.getLogger().info("Map " + instance.getInstanceId()
                        + " creation finished but no players joined. Destroying.");
                destroyMap(instance.getInstanceId());
            }
        });
    }

    public boolean joinMap(Player player, String instanceId) {
        MapInstance instance = activeInstances.get(instanceId);

        if (instance == null) {
            return false;
        }

        // Prevent joining same map
        if (instanceId.equals(playerToInstance.get(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "You are already in this map!");
            return false;
        }

        if (instance.getState() == MapState.CREATING) {
            player.sendMessage(ChatColor.YELLOW
                    + "Please be patient. It is taking longer than usual to join/prepare the map. Please contact an admin if this issue persists.");
            instance.addWaitingPlayer(player.getUniqueId());
            return true;
        }

        if (instance.getState() != MapState.WAITING && instance.getState() != MapState.RUNNING) {
            player.sendMessage(ChatColor.RED + "Map is not ready yet!");
            return false;
        }

        if (!configManager.canPlayerJoinMap(player, instance.getMapName())) {
            player.sendMessage(ChatColor.RED + "You don't have permission to join this map!");
            return false;
        }

        if (instance.getPlayers().size() >= configManager.getMaxPlayers()) {
            player.sendMessage(ChatColor.RED + "Map is full!");
            return false;
        }

        instance.addPlayer(player.getUniqueId());
        playerToInstance.put(player.getUniqueId(), instanceId);

        // Try Paper's async teleport, fallback to sync if not available
        try {
            player.teleportAsync(instance.getSpawnLocation()).thenAccept(success -> {
                if (success) {
                    player.setGameMode(GameMode.ADVENTURE);
                    player.sendMessage(ChatColor.GREEN + "Welcome to " + instance.getMapName() + "!");
                }
            });
        } catch (NoSuchMethodError e) {
            // Fallback for Spigot
            player.teleport(instance.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(ChatColor.GREEN + "Welcome to " + instance.getMapName() + "!");
        }

        if (instance.getState() == MapState.WAITING) {
            instance.setState(MapState.RUNNING);
        }

        return true;
    }

    public void leaveMap(Player player) {
        String instanceId = playerToInstance.remove(player.getUniqueId());

        if (instanceId == null) {
            return;
        }

        MapInstance instance = activeInstances.get(instanceId);

        if (instance != null) {
            instance.removePlayer(player.getUniqueId());

            Location spawnLoc = Bukkit.getWorlds().get(0).getSpawnLocation();

            // Try Paper's async teleport, fallback to sync if not available
            try {
                player.teleportAsync(spawnLoc).thenAccept(success -> {
                    if (success) {
                        player.setGameMode(GameMode.SURVIVAL);
                        player.setAllowFlight(false);
                        player.setFlying(false);
                        player.sendMessage(ChatColor.YELLOW + "You left the map.");
                    }
                });
            } catch (NoSuchMethodError e) {
                // Fallback for Spigot
                player.teleport(spawnLoc);
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage(ChatColor.YELLOW + "You left the map.");
            }

            if (instance.getPlayers().isEmpty()) {
                instance.setState(MapState.ENDING);

                // Get cleanup delay from config (default 15 seconds)
                long cleanupDelay = configManager.getCleanupDelay();

                plugin.getLogger()
                        .info("Map " + instanceId + " is empty. Scheduling deletion in " + cleanupDelay + " seconds.");

                // Schedule world cleanup after delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Check again if still empty
                    if (instance.getPlayers().isEmpty() && instance.getWaitingPlayers().isEmpty()) {
                        destroyMap(instanceId);
                    } else {
                        plugin.getLogger().info("Map " + instanceId + " is no longer empty, cancelling deletion.");
                        instance.setState(MapState.RUNNING);
                    }
                }, cleanupDelay * 20L); // Convert seconds to ticks
            }
        }
    }

    private void destroyMap(String instanceId) {
        MapInstance instance = activeInstances.remove(instanceId);

        if (instance != null && instance.getWorld() != null) {
            instance.setState(MapState.DESTROYING);

            World world = instance.getWorld();
            String worldName = world.getName();

            plugin.getLogger().info("Destroying map world: " + worldName);

            // First, unload the world properly
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Kick any players that might have joined in the last tick (unlikely but safe)
                for (Player p : world.getPlayers()) {
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }

                boolean unloaded = Bukkit.unloadWorld(world, false);

                if (unloaded) {
                    plugin.getLogger().info("World unloaded: " + worldName);

                    // Wait a bit for the world to fully unload, then delete
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
                        if (worldFolder.exists()) {
                            // Force delete
                            boolean deleted = deleteFolder(worldFolder);
                            if (deleted) {
                                plugin.getLogger().info("World folder deleted: " + worldName);
                            } else {
                                plugin.getLogger().warning("Failed to delete world folder: " + worldName);
                            }
                        }
                    }, 40L); // 2 second delay
                } else {
                    plugin.getLogger().warning("Failed to unload world: " + worldName);
                }
            });
        }
    }

    private void deleteWorldFolder(World world) {
        java.io.File worldFolder = world.getWorldFolder();
        deleteFolder(worldFolder);
    }

    private boolean deleteFolder(java.io.File folder) {
        if (folder.exists()) {
            java.io.File[] files = folder.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isDirectory()) {
                        deleteFolder(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return folder.delete();
        }
        return false;
    }

    public MapInstance getInstance(String instanceId) {
        return activeInstances.get(instanceId);
    }

    public void cleanup() {
        for (String instanceId : new ArrayList<>(activeInstances.keySet())) {
            if (!activeInstances.get(instanceId).isPublic()) {
                destroyMap(instanceId);
            }
        }
    }

    private String generateRandomCode() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}