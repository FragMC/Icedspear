package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.models.MapInstance;
import com.stufy.fragmc.icedspear.models.Party;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimerManager {
    private final IcedSpear plugin;
    private final LeaderboardManager leaderboardManager;
    private final PartyManager partyManager;
    private final MapManager mapManager;

    // Track player timers: Player UUID -> Start time in milliseconds
    private final Map<UUID, Long> activeTimers;

    // Track players who have finished: Player UUID -> Finish time
    private final Map<UUID, Long> finishedPlayers;

    // Track players who have moved (started their timer)
    private final Set<UUID> hasStarted;

    // Actionbar update task
    private BukkitTask actionbarTask;

    public TimerManager(IcedSpear plugin, LeaderboardManager leaderboardManager, PartyManager partyManager, MapManager mapManager) {
        this.plugin = plugin;
        this.leaderboardManager = leaderboardManager;
        this.partyManager = partyManager;
        this.mapManager = mapManager;
        this.activeTimers = new ConcurrentHashMap<>();
        this.finishedPlayers = new ConcurrentHashMap<>();
        this.hasStarted = ConcurrentHashMap.newKeySet();

        startActionbarUpdater();
    }

    /**
     * Start the timer for a player (called on first movement)
     */
    public void startTimer(Player player) {
        UUID playerId = player.getUniqueId();

        // Don't start if already finished
        if (finishedPlayers.containsKey(playerId)) {
            return;
        }

        // Don't start if already started
        if (activeTimers.containsKey(playerId)) {
            return;
        }

        activeTimers.put(playerId, System.currentTimeMillis());
        hasStarted.add(playerId);

        player.sendMessage(ChatColor.GREEN + "Timer started! Touch a diamond block to finish.");
    }

    /**
     * Stop the timer when player touches diamond block
     */
    public void stopTimer(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if player has an active timer
        if (!activeTimers.containsKey(playerId)) {
            return;
        }

        // Already finished
        if (finishedPlayers.containsKey(playerId)) {
            return;
        }

        long startTime = activeTimers.get(playerId);
        long endTime = System.currentTimeMillis();
        long timeTaken = endTime - startTime;

        // Mark as finished
        finishedPlayers.put(playerId, timeTaken);
        activeTimers.remove(playerId);

        // Get map instance
        String instanceId = mapManager.getPlayerInstance(playerId);
        if (instanceId == null) return;

        MapInstance instance = mapManager.getInstance(instanceId);
        if (instance == null) return;

        String mapName = instance.getMapName();

        // Format time
        String formattedTime = formatTime(timeTaken);

        // Send completion message to player
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "  ✓ FINISHED!");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  Time: " + ChatColor.WHITE + formattedTime);
        player.sendMessage(ChatColor.YELLOW + "  Map: " + ChatColor.WHITE + mapName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");

        // Add to leaderboard
        int rank = leaderboardManager.addTime(mapName, playerId, player.getName(), timeTaken);

        if (rank <= 10) {
            player.sendMessage(ChatColor.GOLD + "★ " + ChatColor.GREEN + "New personal best! Rank #" + rank + " on " + mapName);
        }

        // Broadcast to party if in one
        String partyCode = partyManager.getPlayerParty(playerId);
        if (partyCode != null) {
            Party party = partyManager.getParty(partyCode);
            if (party != null) {
                String partyMessage = ChatColor.LIGHT_PURPLE + "[Party] " +
                        ChatColor.GREEN + player.getName() +
                        ChatColor.YELLOW + " finished in " +
                        ChatColor.WHITE + formattedTime +
                        ChatColor.YELLOW + "!";

                partyManager.broadcastToParty(party, partyMessage);
            }
        }
    }

    /**
     * Reset player when they leave map
     */
    public void resetPlayer(UUID playerId) {
        activeTimers.remove(playerId);
        finishedPlayers.remove(playerId);
        hasStarted.remove(playerId);
    }

    /**
     * Check if player has finished
     */
    public boolean hasFinished(UUID playerId) {
        return finishedPlayers.containsKey(playerId);
    }

    /**
     * Check if player has started
     */
    public boolean hasStarted(UUID playerId) {
        return hasStarted.contains(playerId);
    }

    /**
     * Get current time for a player
     */
    public long getCurrentTime(UUID playerId) {
        if (!activeTimers.containsKey(playerId)) {
            return 0;
        }

        long startTime = activeTimers.get(playerId);
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Format milliseconds to readable time
     */
    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (milliseconds % 1000) / 10; // Get centiseconds

        if (minutes > 0) {
            return String.format("%d:%02d.%02d", minutes, seconds, millis);
        } else {
            return String.format("%d.%02d", seconds, millis);
        }
    }

    /**
     * Start the actionbar updater task
     */
    private void startActionbarUpdater() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();

                // Check if player is in a map
                String instanceId = mapManager.getPlayerInstance(playerId);
                if (instanceId == null) continue;

                String actionbarText;

                if (finishedPlayers.containsKey(playerId)) {
                    // Player has finished
                    long finishTime = finishedPlayers.get(playerId);
                    actionbarText = ChatColor.GREEN + "✓ Finished: " +
                            ChatColor.WHITE + formatTime(finishTime) +
                            ChatColor.GRAY + " | Type " +
                            ChatColor.YELLOW + "/map leave" +
                            ChatColor.GRAY + " when ready";

                } else if (activeTimers.containsKey(playerId)) {
                    // Player has active timer
                    long currentTime = getCurrentTime(playerId);
                    actionbarText = ChatColor.YELLOW + "⏱ Time: " +
                            ChatColor.WHITE + formatTime(currentTime);

                } else {
                    // Player hasn't started yet
                    actionbarText = ChatColor.GRAY + "Move to start timer...";
                }

                // Send actionbar
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionbarText));
            }
        }, 0L, 5L); // Update every 5 ticks (4 times per second)
    }

    /**
     * Stop the actionbar updater
     */
    public void shutdown() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
        }
    }
}