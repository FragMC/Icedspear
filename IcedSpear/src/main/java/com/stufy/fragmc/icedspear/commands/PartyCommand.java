package com.stufy.fragmc.icedspear.commands;

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

    public PartyCommand(PartyManager partyManager) {
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

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player not found or offline.");
                    return true;
                }
                partyManager.invitePlayer(player, target);
                break;

            case "accept":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party accept <player>");
                    return true;
                }
                partyManager.acceptInvite(player, args[1]);
                break;

            case "acceptrequest":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party acceptrequest <player>");
                    return true;
                }
                partyManager.acceptJoinRequest(player, args[1]);
                break;

            case "request":
            case "requestjoin":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /party request <player>");
                    return true;
                }
                Player requestTarget = Bukkit.getPlayer(args[1]);
                if (requestTarget == null) {
                    player.sendMessage(ChatColor.RED + "Player not found or offline.");
                    return true;
                }
                partyManager.requestJoin(player, requestTarget);
                break;

            case "chat":
                if (args.length < 2) {
                    partyManager.togglePartyChat(player);
                } else {
                    StringBuilder msg = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        msg.append(args[i]).append(" ");
                    }
                    partyManager.sendPartyChat(player, msg.toString());
                }
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
        player.sendMessage(ChatColor.GRAY + "Share this code with friends to invite them!");
    }

    private void handleJoinParty(Player player, String code) {
        boolean success = partyManager.joinParty(player, code);

        if (!success) {
            player.sendMessage(ChatColor.RED + "Invalid party code or you're already in a party!");
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
            player.sendMessage(
                    ChatColor.RED + "Failed to kick player. Are you the leader? Is the player in your party?");
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== IcedSpear Party Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/party create" + ChatColor.GRAY + " - Create a new party");
        player.sendMessage(ChatColor.YELLOW + "/party join <code>" + ChatColor.GRAY + " - Join a party");
        player.sendMessage(ChatColor.YELLOW + "/party leave" + ChatColor.GRAY + " - Leave your party");
        player.sendMessage(
                ChatColor.YELLOW + "/party map <mapname>" + ChatColor.GRAY + " - Start a party map (leader only)");
        player.sendMessage(
                ChatColor.YELLOW + "/party kick <player>" + ChatColor.GRAY + " - Kick a player (leader only)");
        player.sendMessage(ChatColor.YELLOW + "/party list" + ChatColor.GRAY + " - List party members");
        player.sendMessage(ChatColor.YELLOW + "/party invite <player>" + ChatColor.GRAY + " - Invite a friend");
        player.sendMessage(ChatColor.YELLOW + "/party accept <player>" + ChatColor.GRAY + " - Accept an invite");
        player.sendMessage(
                ChatColor.YELLOW + "/party request <player>" + ChatColor.GRAY + " - Request to join a friend's party");
        player.sendMessage(
                ChatColor.YELLOW + "/party acceptrequest <player>" + ChatColor.GRAY + " - Accept a join request");
        player.sendMessage(
                ChatColor.YELLOW + "/party chat [message]" + ChatColor.GRAY + " - Toggle or send party chat");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "join", "leave", "map", "kick", "list", "invite", "accept", "request",
                    "acceptrequest", "chat");
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
            if (Arrays.asList("invite", "accept", "request", "acceptrequest").contains(args[0].toLowerCase())) {
                return null; // Return null to show online players
            }
        }

        return new ArrayList<>();
    }
}