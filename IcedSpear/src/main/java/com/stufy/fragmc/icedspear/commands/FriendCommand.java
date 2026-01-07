package com.stufy.fragmc.icedspear.commands;

import com.stufy.fragmc.icedspear.managers.FriendManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.*;

public class FriendCommand implements CommandExecutor, TabCompleter {
    private final FriendManager friendManager;

    public FriendCommand(FriendManager friendManager) {
        this.friendManager = friendManager;
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
            case "add":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend add <player>");
                    return true;
                }
                handleAddFriend(player, args[1]);
                break;

            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend accept <player>");
                    return true;
                }
                handleAcceptFriend(player, args[1]);
                break;

            case "remove":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /friend remove <player>");
                    return true;
                }
                handleRemoveFriend(player, args[1]);
                break;

            case "list":
                handleListFriends(player);
                break;

            case "requests":
                handleListRequests(player);
                break;

            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private void handleAddFriend(Player player, String targetName) {
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You can't add yourself as a friend!");
            return;
        }

        boolean success = friendManager.sendFriendRequest(player, target);

        if (!success) {
            player.sendMessage(ChatColor.RED + "You're already friends with this player!");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Friend request sent to " + target.getName() + "!");
        target.sendMessage(ChatColor.YELLOW + player.getName() + " sent you a friend request!");
        target.sendMessage(ChatColor.GRAY + "Use /friend accept " + player.getName() + " to accept.");
    }

    private void handleAcceptFriend(Player player, String requesterName) {
        Player requester = Bukkit.getPlayer(requesterName);

        if (requester == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return;
        }

        boolean success = friendManager.acceptFriendRequest(player, requester);

        if (!success) {
            player.sendMessage(ChatColor.RED + "No friend request from this player!");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "You're now friends with " + requester.getName() + "!");
        requester.sendMessage(ChatColor.GREEN + player.getName() + " accepted your friend request!");
    }

    private void handleRemoveFriend(Player player, String friendName) {
        OfflinePlayer friend = Bukkit.getOfflinePlayer(friendName);

        boolean success = friendManager.removeFriend(player, friend.getUniqueId());

        if (!success) {
            player.sendMessage(ChatColor.RED + "This player is not your friend!");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "You removed " + friendName + " from your friends list.");
    }

    private void handleListFriends(Player player) {
        Set<UUID> friends = friendManager.getFriends(player.getUniqueId());

        if (friends.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no friends yet. Use /friend add <player> to add friends!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Your Friends ===");

        for (UUID friendId : friends) {
            OfflinePlayer friend = Bukkit.getOfflinePlayer(friendId);
            String status = friend.isOnline() ? ChatColor.GREEN + "●" : ChatColor.GRAY + "●";
            player.sendMessage(status + " " + ChatColor.WHITE + friend.getName());
        }
    }

    private void handleListRequests(Player player) {
        Set<UUID> requests = friendManager.getPendingRequests(player.getUniqueId());

        if (requests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no pending friend requests.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Friend Requests ===");

        for (UUID requesterId : requests) {
            OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterId);
            player.sendMessage(ChatColor.YELLOW + "• " + ChatColor.WHITE + requester.getName());
        }

        player.sendMessage(ChatColor.GRAY + "Use /friend accept <player> to accept a request.");
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== IcedSpear Friend Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/friend add <player>" + ChatColor.GRAY + " - Send a friend request");
        player.sendMessage(ChatColor.YELLOW + "/friend accept <player>" + ChatColor.GRAY + " - Accept a friend request");
        player.sendMessage(ChatColor.YELLOW + "/friend remove <player>" + ChatColor.GRAY + " - Remove a friend");
        player.sendMessage(ChatColor.YELLOW + "/friend list" + ChatColor.GRAY + " - View your friends");
        player.sendMessage(ChatColor.YELLOW + "/friend requests" + ChatColor.GRAY + " - View friend requests");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("add", "accept", "remove", "list", "requests");
        }
        return new ArrayList<>();
    }
}