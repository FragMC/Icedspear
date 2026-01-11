package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.models.MapInstance;
import com.stufy.fragmc.icedspear.models.MapState;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
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
            boolean worldExists = new java.io.File(Bukkit.getWorldContainer(), instance.getInstanceId()).exists();

            WorldCreator worldCreator = new WorldCreator(instance.getInstanceId());
            worldCreator.environment(World.Environment.NORMAL);
            worldCreator.type(WorldType.FLAT);
            worldCreator.generator(new org.bukkit.generator.ChunkGenerator() {
                @Override
                public void generateNoise(org.bukkit.generator.WorldInfo worldInfo,
                                          Random random, int chunkX, int chunkZ,
                                          org.bukkit.generator.ChunkGenerator.ChunkData chunkData) {
                }

                @Override
                public void generateSurface(org.bukkit.generator.WorldInfo worldInfo,
                                            Random random, int chunkX, int chunkZ,
                                            org.bukkit.generator.ChunkGenerator.ChunkData chunkData) {
                }

                @Override
                public boolean shouldGenerateNoise() { return false; }
                @Override
                public boolean shouldGenerateSurface() { return false; }
                @Override
                public boolean shouldGenerateCaves() { return false; }
                @Override
                public boolean shouldGenerateDecorations() { return false; }
                @Override
                public boolean shouldGenerateMobs() { return false; }
                @Override
                public boolean shouldGenerateStructures() { return false; }
            });

            World world = worldCreator.createWorld();
            if (world != null) {
                instance.setWorld(world);

                // Apply all world settings from config
                configManager.applyWorldSettings(world);

                if (!worldExists) {
                    pasteSchematic(instance);
                } else {
                    plugin.getLogger().info("Map world " + instance.getInstanceId() + " already exists. Skipping schematic paste.");
                    scanForGoldBlock(instance, new Location(world, 0, 100, 0));
                }

                // Schedule cleanup if no one joins within the configured time
                scheduleNoJoinCleanup(instance);
            }
        });
    }

    private void scheduleNoJoinCleanup(MapInstance instance) {
        long delay = configManager.getNoJoinCleanupDelay();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Check if map is still in CREATING or WAITING state and has no players
            if ((instance.getState() == MapState.CREATING || instance.getState() == MapState.WAITING)
                    && instance.getPlayers().isEmpty()
                    && instance.getWaitingPlayers().isEmpty()) {

                plugin.getLogger().info("Map " + instance.getInstanceId() + " had no players join within " + delay + " seconds. Destroying.");
                destroyMap(instance.getInstanceId());
            }
        }, delay * 20L);
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

            String schematicName = schematicManager.getSchematicForMap(instance.getMapName());
            org.bukkit.util.BoundingBox bounds = schematicManager.getSchematicBounds(schematicName, pasteLocation);

            if (bounds != null) {
                plugin.getLogger().info("Scanning schematic bounds for gold block: " + bounds.toString());

                int minX = (int) bounds.getMinX();
                int maxX = (int) bounds.getMaxX();
                int minY = (int) Math.max(world.getMinHeight(), bounds.getMinY());
                int maxY = (int) Math.min(world.getMaxHeight(), bounds.getMaxY());
                int minZ = (int) bounds.getMinZ();
                int maxZ = (int) bounds.getMaxZ();

                outerLoop: for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int y = minY; y <= maxY; y++) {
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
            } else {
                plugin.getLogger().warning("Could not determine schematic bounds for gold block scan.");
            }

            if (goldBlockLocation == null) {
                plugin.getLogger().warning("No gold block found in schematic! Using default spawn location.");
            }

            Location finalGoldLocation = goldBlockLocation != null ? goldBlockLocation : pasteLocation.clone().add(0, 1, 0);

            Bukkit.getScheduler().runTask(plugin, () -> {
                instance.setSpawnLocation(finalGoldLocation);
                plugin.getLogger().info("Map ready! Spawn set at: X=" + finalGoldLocation.getBlockX() +
                        " Y=" + finalGoldLocation.getBlockY() +
                        " Z=" + finalGoldLocation.getBlockZ());
                instance.setState(MapState.WAITING);

                // Teleport waiting players
                for (UUID playerId : instance.getWaitingPlayers()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        if (!configManager.canPlayerJoinMap(player, instance.getMapName())) {
                            player.sendMessage(ChatColor.RED + "You don't have permission to join this map!");
                            continue;
                        }

                        if (instance.getPlayers().size() >= configManager.getMaxPlayers()) {
                            player.sendMessage(ChatColor.RED + "Map is full!");
                            continue;
                        }

                        instance.addPlayer(playerId);
                        playerToInstance.put(playerId, instance.getInstanceId());

                        try {
                            player.teleportAsync(finalGoldLocation).thenAccept(success -> {
                                if (success) {
                                    player.setGameMode(configManager.getDefaultGameMode());
                                    player.sendMessage(ChatColor.GREEN + "Map is ready! Welcome to " + instance.getMapName() + "!");
                                    configManager.executeOnJoinCommands(player);
                                }
                            });
                        } catch (NoSuchMethodError e) {
                            player.teleport(finalGoldLocation);
                            player.setGameMode(configManager.getDefaultGameMode());
                            player.sendMessage(ChatColor.GREEN + "Map is ready! Welcome to " + instance.getMapName() + "!");
                            configManager.executeOnJoinCommands(player);
                        }
                    }
                }
                instance.clearWaitingPlayers();

                if (!instance.getPlayers().isEmpty()) {
                    instance.setState(MapState.RUNNING);
                }
            });
        });
    }

    public boolean joinMap(Player player, String instanceId) {
        MapInstance instance = activeInstances.get(instanceId);

        if (instance == null) {
            return false;
        }

        if (instanceId.equals(playerToInstance.get(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "You are already in this map!");
            return false;
        }

        if (instance.getState() == MapState.CREATING) {
            player.sendMessage(ChatColor.YELLOW + "Please be patient. It is taking longer than usual to join/prepare the map. Please contact an admin if this issue persists.");
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

        try {
            player.teleportAsync(instance.getSpawnLocation()).thenAccept(success -> {
                if (success) {
                    player.setGameMode(configManager.getDefaultGameMode());
                    player.sendMessage(ChatColor.GREEN + "Welcome to " + instance.getMapName() + "!");
                    configManager.executeOnJoinCommands(player);
                }
            });
        } catch (NoSuchMethodError e) {
            player.teleport(instance.getSpawnLocation());
            player.setGameMode(configManager.getDefaultGameMode());
            player.sendMessage(ChatColor.GREEN + "Welcome to " + instance.getMapName() + "!");
            configManager.executeOnJoinCommands(player);
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

            // Reset player's timer
            if (plugin.getTimerManager() != null) {
                plugin.getTimerManager().resetPlayer(player.getUniqueId());
            }

            Location spawnLoc = Bukkit.getWorlds().get(0).getSpawnLocation();

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
                player.teleport(spawnLoc);
                player.setGameMode(GameMode.SURVIVAL);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage(ChatColor.YELLOW + "You left the map.");
            }

            if (instance.getPlayers().isEmpty() && instance.getWaitingPlayers().isEmpty()) {
                instance.setState(MapState.ENDING);

                long cleanupDelay = configManager.getCleanupDelay();
                plugin.getLogger().info("Map " + instanceId + " is empty. Scheduling deletion in " + cleanupDelay + " seconds.");

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (instance.getPlayers().isEmpty() && instance.getWaitingPlayers().isEmpty()) {
                        destroyMap(instanceId);
                    } else {
                        plugin.getLogger().info("Map " + instanceId + " is no longer empty, cancelling deletion.");
                        instance.setState(MapState.RUNNING);
                    }
                }, cleanupDelay * 20L);
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

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : world.getPlayers()) {
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }

                boolean unloaded = Bukkit.unloadWorld(world, false);

                if (unloaded) {
                    plugin.getLogger().info("World unloaded: " + worldName);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        java.io.File worldFolder = new java.io.File(Bukkit.getWorldContainer(), worldName);
                        if (worldFolder.exists()) {
                            boolean deleted = deleteFolder(worldFolder);
                            if (deleted) {
                                plugin.getLogger().info("World folder deleted: " + worldName);
                            } else {
                                plugin.getLogger().warning("Failed to delete world folder: " + worldName);
                            }
                        }
                    }, 40L);
                } else {
                    plugin.getLogger().warning("Failed to unload world: " + worldName);
                }
            });
        }
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