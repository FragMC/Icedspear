package com.stufy.fragmc.icedspear.listeners;

import com.stufy.fragmc.icedspear.api.events.PlayerLeaveMapEvent;
import com.stufy.fragmc.icedspear.managers.ConfigManager;
import com.stufy.fragmc.icedspear.managers.MapManager;
import com.stufy.fragmc.icedspear.managers.TimerManager;
import com.stufy.fragmc.icedspear.models.MapInstance;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MapListener implements Listener {
    private final MapManager mapManager;
    private final ConfigManager configManager;
    private final TimerManager timerManager;

    public MapListener(MapManager mapManager, ConfigManager configManager, TimerManager timerManager) {
        this.mapManager = mapManager;
        this.configManager = configManager;
        this.timerManager = timerManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Paper optimization: Only check if player actually moved blocks
        if (!event.hasChangedBlock()) {
            return;
        }

        Player player = event.getPlayer();
        String instanceId = mapManager.getPlayerInstance(player.getUniqueId());

        if (instanceId == null) {
            return;
        }

        MapInstance instance = mapManager.getInstance(instanceId);
        if (instance == null) {
            return;
        }

        // Start timer on first movement (if not already started or finished)
        if (!timerManager.hasStarted(player.getUniqueId()) && !timerManager.hasFinished(player.getUniqueId())) {
            timerManager.startTimer(player);
        }

        // Check if player is standing on a diamond block
        if (event.getTo().getBlock().getRelative(0, -1, 0).getType() == Material.DIAMOND_BLOCK) {
            // Stop timer if they haven't finished yet
            if (!timerManager.hasFinished(player.getUniqueId())) {
                timerManager.stopTimer(player);
            }

            // Give flight ability
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String instanceId = mapManager.getPlayerInstance(player.getUniqueId());

        if (instanceId != null) {
            MapInstance instance = mapManager.getInstance(instanceId);

            // Fire leave event before actually leaving
            if (instance != null) {
                PlayerLeaveMapEvent leaveEvent = new PlayerLeaveMapEvent(
                        player,
                        instanceId,
                        instance.getMapName()
                );
                Bukkit.getPluginManager().callEvent(leaveEvent);
            }

            // Reset timer
            timerManager.resetPlayer(player.getUniqueId());

            mapManager.leaveMap(player);
        }
    }
}