package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.models.LeaderboardEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardManager {
    private final IcedSpear plugin;
    private final File leaderboardFile;
    private FileConfiguration leaderboardConfig;

    // Map name -> List of leaderboard entries
    private final Map<String, List<LeaderboardEntry>> leaderboards;

    public LeaderboardManager(IcedSpear plugin) {
        this.plugin = plugin;
        this.leaderboardFile = new File(plugin.getDataFolder(), "leaderboards.yml");
        this.leaderboards = new HashMap<>();

        loadLeaderboards();
    }

    /**
     * Load leaderboards from file
     */
    private void loadLeaderboards() {
        if (!leaderboardFile.exists()) {
            try {
                leaderboardFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create leaderboards.yml");
            }
        }

        leaderboardConfig = YamlConfiguration.loadConfiguration(leaderboardFile);

        // Load all map leaderboards
        if (leaderboardConfig.contains("leaderboards")) {
            ConfigurationSection section = leaderboardConfig.getConfigurationSection("leaderboards");
            if (section != null) {
                for (String mapName : section.getKeys(false)) {
                    List<LeaderboardEntry> entries = new ArrayList<>();

                    ConfigurationSection mapSection = section.getConfigurationSection(mapName);
                    if (mapSection != null) {
                        for (String key : mapSection.getKeys(false)) {
                            String playerName = mapSection.getString(key + ".name");
                            String playerUuid = mapSection.getString(key + ".uuid");
                            long time = mapSection.getLong(key + ".time");
                            long timestamp = mapSection.getLong(key + ".timestamp", System.currentTimeMillis());

                            if (playerName != null && playerUuid != null) {
                                entries.add(new LeaderboardEntry(
                                        UUID.fromString(playerUuid),
                                        playerName,
                                        time,
                                        timestamp
                                ));
                            }
                        }
                    }

                    // Sort by time (ascending)
                    entries.sort(Comparator.comparingLong(LeaderboardEntry::getTime));
                    leaderboards.put(mapName, entries);
                }
            }
        }
    }

    /**
     * Save leaderboards to file
     */
    private void saveLeaderboards() {
        // Clear existing data
        leaderboardConfig.set("leaderboards", null);

        // Save all leaderboards
        for (Map.Entry<String, List<LeaderboardEntry>> entry : leaderboards.entrySet()) {
            String mapName = entry.getKey();
            List<LeaderboardEntry> entries = entry.getValue();

            for (int i = 0; i < entries.size(); i++) {
                LeaderboardEntry lbEntry = entries.get(i);
                String path = "leaderboards." + mapName + "." + i;

                leaderboardConfig.set(path + ".name", lbEntry.getPlayerName());
                leaderboardConfig.set(path + ".uuid", lbEntry.getPlayerId().toString());
                leaderboardConfig.set(path + ".time", lbEntry.getTime());
                leaderboardConfig.set(path + ".timestamp", lbEntry.getTimestamp());
            }
        }

        try {
            leaderboardConfig.save(leaderboardFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save leaderboards.yml");
        }
    }

    /**
     * Add a time to the leaderboard
     * @param mapName The map name
     * @param playerId Player UUID
     * @param playerName Player name
     * @param time Time in milliseconds
     * @return The player's rank (1-based)
     */
    public int addTime(String mapName, UUID playerId, String playerName, long time) {
        List<LeaderboardEntry> entries = leaderboards.computeIfAbsent(mapName, k -> new ArrayList<>());

        // Check if player already has a time
        LeaderboardEntry existingEntry = null;
        for (LeaderboardEntry entry : entries) {
            if (entry.getPlayerId().equals(playerId)) {
                existingEntry = entry;
                break;
            }
        }

        // Only update if new time is better or player has no time
        if (existingEntry == null || time < existingEntry.getTime()) {
            if (existingEntry != null) {
                entries.remove(existingEntry);
            }

            LeaderboardEntry newEntry = new LeaderboardEntry(playerId, playerName, time, System.currentTimeMillis());
            entries.add(newEntry);

            // Sort by time (ascending - lower is better)
            entries.sort(Comparator.comparingLong(LeaderboardEntry::getTime));

            // Keep only top 100
            if (entries.size() > 100) {
                entries = entries.subList(0, 100);
                leaderboards.put(mapName, entries);
            }

            saveLeaderboards();
        }

        // Find player's rank
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getPlayerId().equals(playerId)) {
                return i + 1; // Return 1-based rank
            }
        }

        return -1; // Not found (shouldn't happen)
    }

    /**
     * Get top N entries for a map
     * @param mapName The map name
     * @param limit Maximum number of entries
     * @return List of top entries
     */
    public List<LeaderboardEntry> getTopTimes(String mapName, int limit) {
        List<LeaderboardEntry> entries = leaderboards.get(mapName);

        if (entries == null || entries.isEmpty()) {
            return new ArrayList<>();
        }

        return entries.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get a player's best time for a map
     * @param mapName The map name
     * @param playerId Player UUID
     * @return Time in milliseconds, or -1 if not found
     */
    public long getPlayerTime(String mapName, UUID playerId) {
        List<LeaderboardEntry> entries = leaderboards.get(mapName);

        if (entries == null) {
            return -1;
        }

        for (LeaderboardEntry entry : entries) {
            if (entry.getPlayerId().equals(playerId)) {
                return entry.getTime();
            }
        }

        return -1;
    }

    /**
     * Get a player's rank for a map
     * @param mapName The map name
     * @param playerId Player UUID
     * @return Rank (1-based), or -1 if not found
     */
    public int getPlayerRank(String mapName, UUID playerId) {
        List<LeaderboardEntry> entries = leaderboards.get(mapName);

        if (entries == null) {
            return -1;
        }

        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getPlayerId().equals(playerId)) {
                return i + 1;
            }
        }

        return -1;
    }

    /**
     * Get all leaderboards
     * @return Map of map name to leaderboard entries
     */
    public Map<String, List<LeaderboardEntry>> getAllLeaderboards() {
        return new HashMap<>(leaderboards);
    }
}