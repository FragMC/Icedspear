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

                // Paste schematic
                pasteSchematic(instance);
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

        boolean success = schematicManager.pasteSchematic(schematicName, pasteLocation);

        if (success) {
            plugin.getLogger().info("Schematic pasted successfully, scanning for gold block...");
            scanForGoldBlock(instance, pasteLocation);
        } else {
            plugin.getLogger().severe("Failed to paste schematic for map: " + instance.getMapName());
            instance.setState(MapState.ERROR);
        }
    }

    private void scanForGoldBlock(MapInstance instance, Location pasteLocation) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location goldBlockLocation = null;
            World world = instance.getWorld();

            plugin.getLogger().info("Scanning entire world for gold block...");

            // Get world height limits
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();

            // Scan a large area - 500x500 blocks around spawn
            outerLoop:
            for (int x = -250; x <= 250; x++) {
                for (int z = -250; z <= 250; z++) {
                    for (int y = minY; y < maxY; y++) {
                        Location checkLoc = new Location(world, x, y, z);
                        Block block = world.getBlockAt(checkLoc);

                        if (block.getType() == Material.GOLD_BLOCK) {
                            goldBlockLocation = checkLoc.add(0.5, 1, 0.5);
                            plugin.getLogger().info("Found gold block at: X=" + goldBlockLocation.getBlockX() +
                                    " Y=" + goldBlockLocation.getBlockY() +
                                    " Z=" + goldBlockLocation.getBlockZ());
                            break outerLoop;
                        }
                    }
                }
            }

            if (goldBlockLocation == null) {
                plugin.getLogger().warning("No gold block found in map! Using default spawn location.");
            }

            Location finalGoldLocation = goldBlockLocation != null ? goldBlockLocation : pasteLocation.clone().add(0, 1, 0);

            Bukkit.getScheduler().runTask(plugin, () -> {
                instance.setSpawnLocation(finalGoldLocation);
                plugin.getLogger().info("Map ready! Spawn set at: X=" + finalGoldLocation.getBlockX() +
                        " Y=" + finalGoldLocation.getBlockY() +
                        " Z=" + finalGoldLocation.getBlockZ());
                instance.setState(MapState.WAITING);
            });
        });
    }

    public boolean joinMap(Player player, String instanceId) {
        MapInstance instance = activeInstances.get(instanceId);

        if (instance == null) {
            return false;
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

            if (instance.getPlayers().isEmpty() && !instance.isPublic()) {
                instance.setState(MapState.ENDING);

                // Get cleanup delay from config (default 15 seconds)
                long cleanupDelay = configManager.getCleanupDelay();

                // Schedule world cleanup after delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    // Check again if still empty
                    if (instance.getPlayers().isEmpty()) {
                        destroyMap(instanceId);
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
                boolean unloaded = Bukkit.unloadWorld(world, false);

                if (unloaded) {
                    plugin.getLogger().info("World unloaded: " + worldName);

                    // Wait a bit for the world to fully unload, then delete
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
                        if (worldFolder.exists()) {
                            deleteFolder(worldFolder);
                            plugin.getLogger().info("World folder deleted: " + worldName);
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

    private void deleteFolder(java.io.File folder) {
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
            folder.delete();
        }
    }

    public MapInstance getInstance(String instanceId) {
        return activeInstances.get(instanceId);
    }

    public String getPlayerInstance(UUID playerId) {
        return playerToInstance.get(playerId);
    }

    public Map<String, MapInstance> getActiveInstances() {
        return new HashMap<>(activeInstances);
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