package com.stufy.fragmc.icedspear.listeners;

import com.stufy.fragmc.icedspear.managers.MapManager;
import com.stufy.fragmc.icedspear.models.MapInstance;
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

    public MapListener(MapManager mapManager) {
        this.mapManager = mapManager;
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

        // Check if player is standing on a diamond block
        if (event.getTo().getBlock().getRelative(0, -1, 0).getType() == Material.DIAMOND_BLOCK) {
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
            mapManager.leaveMap(player);
        }
    }
}