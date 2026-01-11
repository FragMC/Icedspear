package com.stufy.fragmc.icedspear.commands;

import com.stufy.fragmc.icedspear.managers.LeaderboardManager;
import com.stufy.fragmc.icedspear.managers.MapManager;
import com.stufy.fragmc.icedspear.managers.SchematicManager;
import com.stufy.fragmc.icedspear.models.LeaderboardEntry;
import com.stufy.fragmc.icedspear.models.MapInstance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {
    private final LeaderboardManager leaderboardManager;
    private final MapManager mapManager;
    private final SchematicManager schematicManager;

    public LeaderboardCommand(LeaderboardManager leaderboardManager, MapManager mapManager, SchematicManager schematicManager) {
        this.leaderboardManager = leaderboardManager;
        this.mapManager = mapManager;
        this.schematicManager = schematicManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        // If no args, show leaderboard for current map
        if (args.length == 0) {
            String instanceId = mapManager.getPlayerInstance(player.getUniqueId());

            if (instanceId == null) {
                player.sendMessage(ChatColor.RED + "You must be in a map or specify a map name!");
                player.sendMessage(ChatColor.GRAY + "Usage: /leaderboard <mapname>");
                return true;
            }

            MapInstance instance = mapManager.getInstance(instanceId);
            if (instance == null) {
                player.sendMessage(ChatColor.RED + "Error getting map instance!");
                return true;
            }

            showLeaderboard(player, instance.getMapName());
            return true;
        }

        // Show leaderboard for specified map
        String mapName = args[0];
        showLeaderboard(player, mapName);

        return true;
    }

    private void showLeaderboard(Player player, String mapName) {
        List<LeaderboardEntry> topTimes = leaderboardManager.getTopTimes(mapName, 10);

        if (topTimes.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No times recorded for " + mapName + " yet!");
            player.sendMessage(ChatColor.GRAY + "Be the first to set a time!");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.YELLOW + ChatColor.BOLD.toString() + "  " + mapName + " - Leaderboard");
        player.sendMessage("");

        for (int i = 0; i < topTimes.size(); i++) {
            LeaderboardEntry entry = topTimes.get(i);
            int rank = i + 1;

            String rankColor;
            String rankSymbol;

            if (rank == 1) {
                rankColor = ChatColor.GOLD.toString();
                rankSymbol = "🥇";
            } else if (rank == 2) {
                rankColor = ChatColor.GRAY.toString();
                rankSymbol = "🥈";
            } else if (rank == 3) {
                rankColor = ChatColor.GOLD.toString();
                rankSymbol = "🥉";
            } else {
                rankColor = ChatColor.WHITE.toString();
                rankSymbol = rank + ".";
            }

            // Highlight player's own time
            boolean isPlayerTime = entry.getPlayerId().equals(player.getUniqueId());
            String nameColor = isPlayerTime ? ChatColor.GREEN.toString() + ChatColor.BOLD : ChatColor.WHITE.toString();

            player.sendMessage(
                    rankColor + "  " + rankSymbol + " " +
                            nameColor + entry.getPlayerName() +
                            ChatColor.GRAY + " - " +
                            ChatColor.YELLOW + entry.getFormattedTime()
            );
        }

        // Show player's rank if not in top 10
        int playerRank = leaderboardManager.getPlayerRank(mapName, player.getUniqueId());
        if (playerRank > 10) {
            long playerTime = leaderboardManager.getPlayerTime(mapName, player.getUniqueId());
            String formattedTime = formatTime(playerTime);

            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "  Your Rank: " +
                    ChatColor.YELLOW + "#" + playerRank +
                    ChatColor.GRAY + " - " +
                    ChatColor.WHITE + formattedTime);
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (milliseconds % 1000) / 10;

        if (minutes > 0) {
            return String.format("%d:%02d.%02d", minutes, seconds, millis);
        } else {
            return String.format("%d.%02d", seconds, millis);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Return all available map names
            Set<String> maps = schematicManager.getAllMaps().keySet();
            return new ArrayList<>(maps);
        }

        return new ArrayList<>();
    }
}