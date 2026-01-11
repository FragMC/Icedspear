package com.stufy.fragmc.icedspear.commands;

import com.stufy.fragmc.icedspear.managers.FriendManager;
import com.stufy.fragmc.icedspear.managers.PartyManager;
import com.stufy.fragmc.icedspear.models.Party;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.*;

public class PartyCommand implements CommandExecutor, TabCompleter {
    private final PartyManager partyManager;
    private final FriendManager friendManager;

    public PartyCommand(PartyManager partyManager, FriendManager friendManager) {
        this.partyManager = partyManager;
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
            case "create":
                handleCreateParty(player);
                break;

            case "join":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party join <code>");
                    return true;
                }
                handleJoinParty(player, args[1]);
                break;

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party invite <player>");
                    return true;
                }
                handleInviteParty(player, args[1]);
                break;

            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party accept <player>");
                    return true;
                }
                handleAcceptInvite(player, args[1]);
                break;

            case "leave":
                handleLeaveParty(player);
                break;

            case "map":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party map <mapname>");
                    return true;
                }
                handlePartyMap(player, args[1]);
                break;

            case "list":
                handleListParty(player);
                break;

            case "kick":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party kick <player>");
                    return true;
                }
                handleKickParty(player, args[1]);
                break;

            case "chat":
            case "c":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party chat <message>");
                    return true;
                }
                handlePartyChat(player, args);
                break;

            default:
                sendUsage(player);
                break;
        }

        return true;
    }

    private void handleCreateParty(Player player) {
        String code = partyManager.createParty(player);

        if (code == null) {
            player.sendMessage(ChatColor.RED + "You're already in a party!");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Party created!");
        player.sendMessage(ChatColor.GOLD + "Party code: " + ChatColor.YELLOW + code);
        player.sendMessage(ChatColor.GRAY + "Share this code with friends or use /party invite <player>");
    }

    private void handleJoinParty(Player player, String code) {
        boolean success = partyManager.joinParty(player, code);

        if (!success) {
            player.sendMessage(ChatColor.RED + "Invalid party code or you're already in a party!");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "You joined the party!");
    }

    private void handleInviteParty(Player player, String friendName) {
        partyManager.inviteFriend(player, friendName);
    }

    private void handleAcceptInvite(Player player, String inviterName) {
        boolean success = partyManager.acceptInvite(player, inviterName);

        if (!success) {
            // Error message already sent by manager
            return;
        }

        player.sendMessage(ChatColor.GREEN + "You joined the party!");
    }

    private void handleLeaveParty(Player player) {
        String partyCode = partyManager.getPlayerParty(player.getUniqueId());

        if (partyCode == null) {
            player.sendMessage(ChatColor.RED + "You're not in a party!");
            return;
        }

        partyManager.leaveParty(player);
        player.sendMessage(ChatColor.YELLOW + "You left the party.");
    }

    private void handlePartyMap(Player player, String mapName) {
        String partyCode = partyManager.getPlayerParty(player.getUniqueId());

        if (partyCode == null) {
            player.sendMessage(ChatColor.RED + "You're not in a party!");
            return;
        }

        Party party = partyManager.getParty(partyCode);

        if (!party.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Only the party leader can start maps!");
            return;
        }

        boolean success = partyManager.startPartyMap(player, mapName);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Starting party map: " + mapName);
            player.sendMessage(ChatColor.YELLOW + "All party members will be teleported shortly...");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to start party map!");
        }
    }

    private void handleListParty(Player player) {
        String partyCode = partyManager.getPlayerParty(player.getUniqueId());

        if (partyCode == null) {
            player.sendMessage(ChatColor.RED + "You're not in a party!");
            return;
        }

        Party party = partyManager.getParty(partyCode);

        player.sendMessage(ChatColor.GOLD + "=== Party Members ===");
        player.sendMessage(ChatColor.YELLOW + "Party Code: " + partyCode);

        for (UUID memberId : party.getMembers()) {
            Player member = player.getServer().getPlayer(memberId);
            if (member != null) {
                String prefix = party.getLeader().equals(memberId) ? ChatColor.GOLD + "★ " : ChatColor.GRAY + "• ";
                player.sendMessage(prefix + ChatColor.WHITE + member.getName());
            }
        }
    }

    private void handleKickParty(Player player, String targetName) {
        boolean success = partyManager.kickMember(player, targetName);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "Kicked " + targetName + " from the party.");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to kick player. Are you the leader? Is the player in your party?");
        }
    }

    private void handlePartyChat(Player player, String[] args) {
        // Combine all args after "chat" into message
        StringBuilder message = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            message.append(args[i]);
            if (i < args.length - 1) message.append(" ");
        }

        partyManager.sendPartyChat(player, message.toString());
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== IcedSpear Party Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/party create" + ChatColor.GRAY + " - Create a new party");
        player.sendMessage(ChatColor.YELLOW + "/party join <code>" + ChatColor.GRAY + " - Join a party");
        player.sendMessage(ChatColor.YELLOW + "/party invite <player>" + ChatColor.GRAY + " - Invite a friend (leader only)");
        player.sendMessage(ChatColor.YELLOW + "/party accept <player>" + ChatColor.GRAY + " - Accept party invite");
        player.sendMessage(ChatColor.YELLOW + "/party leave" + ChatColor.GRAY + " - Leave your party");
        player.sendMessage(ChatColor.YELLOW + "/party map <mapname>" + ChatColor.GRAY + " - Start a party map (leader only)");
        player.sendMessage(ChatColor.YELLOW + "/party kick <player>" + ChatColor.GRAY + " - Kick a player (leader only)");
        player.sendMessage(ChatColor.YELLOW + "/party list" + ChatColor.GRAY + " - List party members");
        player.sendMessage(ChatColor.YELLOW + "/party chat <message>" + ChatColor.GRAY + " - Send party message");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "join", "invite", "accept", "leave", "map", "kick", "list", "chat");
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("kick")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    String code = partyManager.getPlayerParty(player.getUniqueId());
                    if (code != null) {
                        Party party = partyManager.getParty(code);
                        if (party != null) {
                            List<String> members = new ArrayList<>();
                            for (UUID id : party.getMembers()) {
                                Player p = player.getServer().getPlayer(id);
                                if (p != null && !p.equals(player)) {
                                    members.add(p.getName());
                                }
                            }
                            return members;
                        }
                    }
                }
                return new ArrayList<>();
            }
            if (args[0].equalsIgnoreCase("invite")) {
                // Show online friends
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    Set<UUID> friends = friendManager.getFriends(player.getUniqueId());
                    List<String> names = new ArrayList<>();
                    for (UUID id : friends) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null && p.isOnline()) {
                            names.add(p.getName());
                        }
                    }
                    return names;
                }
            }
            if (args[0].equalsIgnoreCase("accept")) {
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    Set<UUID> invites = partyManager.getPartyInvites(player.getUniqueId());
                    List<String> names = new ArrayList<>();
                    for (UUID id : invites) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) {
                            names.add(p.getName());
                        }
                    }
                    return names;
                }
            }
        }

        return new ArrayList<>();
    }
}