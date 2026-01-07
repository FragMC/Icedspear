package com.stufy.fragmc.icedspear.commands;

import com.stufy.fragmc.icedspear.managers.MapManager;
import com.stufy.fragmc.icedspear.managers.PartyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.*;

public class MapCommand implements CommandExecutor, TabCompleter {
    private final MapManager mapManager;
    private final PartyManager partyManager;

    public MapCommand(MapManager mapManager, PartyManager partyManager) {
        this.mapManager = mapManager;
        this.partyManager = partyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "public":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /map public <mapname>");
                    return true;
                }
                handlePublicMap(player, args[1]);
                break;

            case "private":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /map private <mapname>");
                    return true;
                }
                handlePrivateMap(player, args[1]);
                break;

            case "join":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /map join <code>");
                    return true;
                }
                handleJoinMap(player, args[1]);
                break;

            case "leave":
                mapManager.leaveMap(player);
                break;

            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private void handlePublicMap(Player player, String mapName) {
        if (!player.hasPermission("icedspear.map.public")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to create public maps!");
            return;
        }

        String instanceId = mapManager.createPublicMap(mapName);
        player.sendMessage(ChatColor.GREEN + "Joining public map: " + mapName);
        player.sendMessage(ChatColor.YELLOW + "Please wait while the map loads...");

        // Wait for map to initialize
        player.getServer().getScheduler().runTaskLater(
                player.getServer().getPluginManager().getPlugin("IcedSpear"),
                () -> mapManager.joinMap(player, instanceId),
                100L
        );
    }

    private void handlePrivateMap(Player player, String mapName) {
        if (!player.hasPermission("icedspear.map.private")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to create private maps!");
            return;
        }

        String instanceId = mapManager.createPrivateMap(mapName);
        String code = instanceId.substring(mapName.length() + 1);

        player.sendMessage(ChatColor.GREEN + "Creating private map: " + mapName);
        player.sendMessage(ChatColor.GOLD + "Map code: " + ChatColor.YELLOW + code);
        player.sendMessage(ChatColor.GRAY + "Share this code with others to let them join!");
        player.sendMessage(ChatColor.YELLOW + "Please wait while the map loads...");

        player.getServer().getScheduler().runTaskLater(
                player.getServer().getPluginManager().getPlugin("IcedSpear"),
                () -> mapManager.joinMap(player, instanceId),
                100L
        );
    }

    private void handleJoinMap(Player player, String code) {
        // Try to find a map instance with this code
        String foundInstance = null;

        for (String instanceId : mapManager.getActiveInstances().keySet()) {
            if (instanceId.contains(code)) {
                foundInstance = instanceId;
                break;
            }
        }

        if (foundInstance == null) {
            player.sendMessage(ChatColor.RED + "No map found with code: " + code);
            return;
        }

        boolean success = mapManager.joinMap(player, foundInstance);

        if (!success) {
            player.sendMessage(ChatColor.RED + "Failed to join map!");
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== IcedSpear Map Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/map public <mapname>" + ChatColor.GRAY + " - Join a public map");
        player.sendMessage(ChatColor.YELLOW + "/map private <mapname>" + ChatColor.GRAY + " - Create a private map");
        player.sendMessage(ChatColor.YELLOW + "/map join <code>" + ChatColor.GRAY + " - Join a map with a code");
        player.sendMessage(ChatColor.YELLOW + "/map leave" + ChatColor.GRAY + " - Leave current map");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("public", "private", "join", "leave");
        }
        return new ArrayList<>();
    }
}