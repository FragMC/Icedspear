package com.stufy.fragmc.icedspear.listeners;

import com.stufy.fragmc.icedspear.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class WorldListener implements Listener {
    private final ConfigManager configManager;

    public WorldListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        handleWorldChange(event.getPlayer(), event.getPlayer().getWorld().getName());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        handleWorldChange(event.getPlayer(), event.getPlayer().getWorld().getName());
    }

    private void handleWorldChange(Player player, String worldName) {
        // Set gamemode
        GameMode gamemode = configManager.getWorldGamemode(worldName);
        if (player.getGameMode() != gamemode) {
            player.setGameMode(gamemode);
        }

        // Run commands
        List<String> commands = configManager.getWorldCommands(worldName);
        for (String command : commands) {
            String processedCommand = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
        }
    }
}
