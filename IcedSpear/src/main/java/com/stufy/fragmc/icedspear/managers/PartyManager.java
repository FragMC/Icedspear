package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.models.Party;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PartyManager {
    private final IcedSpear plugin;
    private final MapManager mapManager;
    private final ConfigManager configManager;
    private final FriendManager friendManager;
    private final Map<String, Party> parties;
    private final Map<UUID, String> playerToParty;
    private final Map<UUID, Set<UUID>> invites;
    private final Map<UUID, Set<UUID>> joinRequests;
    private final Set<UUID> partyChatToggled;

    public PartyManager(IcedSpear plugin, MapManager mapManager, ConfigManager configManager,
            FriendManager friendManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.configManager = configManager;
        this.friendManager = friendManager;
        this.parties = new ConcurrentHashMap<>();
        this.playerToParty = new ConcurrentHashMap<>();
        this.invites = new ConcurrentHashMap<>();
        this.joinRequests = new ConcurrentHashMap<>();
        this.partyChatToggled = ConcurrentHashMap.newKeySet();
    }

    public String createParty(Player leader) {
        if (playerToParty.containsKey(leader.getUniqueId())) {
            return null;
        }

        String code = generatePartyCode();
        Party party = new Party(code, leader.getUniqueId());

        parties.put(code, party);
        playerToParty.put(leader.getUniqueId(), code);

        return code;
    }

    public boolean joinParty(Player player, String code) {
        Party party = parties.get(code);

        if (party == null) {
            return false;
        }

        if (party.getMembers().size() >= configManager.getMaxPlayers()) {
            player.sendMessage(ChatColor.RED + "This party is full!");
            return false;
        }

        if (playerToParty.containsKey(player.getUniqueId())) {
            return false;
        }

        party.addMember(player.getUniqueId());
        playerToParty.put(player.getUniqueId(), code);

        // Notify all party members
        notifyParty(party, ChatColor.GREEN + player.getName() + " joined the party!");

        return true;
    }

    public void leaveParty(Player player) {
        String code = playerToParty.remove(player.getUniqueId());

        if (code == null) {
            return;
        }

        Party party = parties.get(code);

        if (party != null) {
            party.removeMember(player.getUniqueId());

            if (party.getMembers().isEmpty()) {
                disbandParty(code);
            } else if (party.getLeader().equals(player.getUniqueId())) {
                // Party leader left, disband party
                disbandParty(code);
            } else {
                notifyParty(party, ChatColor.YELLOW + player.getName() + " left the party.");
            }
        }
    }

    public boolean kickMember(Player leader, String targetName) {
        String code = playerToParty.get(leader.getUniqueId());
        if (code == null)
            return false;

        Party party = parties.get(code);
        if (party == null || !party.getLeader().equals(leader.getUniqueId()))
            return false;

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null)
            return false;

        if (!party.getMembers().contains(target.getUniqueId()))
            return false;
        if (target.getUniqueId().equals(leader.getUniqueId()))
            return false; // Cannot kick self

        // Remove player
        party.removeMember(target.getUniqueId());
        playerToParty.remove(target.getUniqueId());

        target.sendMessage(ChatColor.RED + "You have been kicked from the party.");
        notifyParty(party, ChatColor.YELLOW + target.getName() + " has been kicked from the party.");

        return true;
    }

    public void disbandParty(String code) {
        Party party = parties.remove(code);

        if (party != null) {
            for (UUID memberId : party.getMembers()) {
                playerToParty.remove(memberId);
            }

            notifyParty(party, ChatColor.RED + "Party has been disbanded.");
        }
    }

    public boolean startPartyMap(Player leader, String mapName) {
        String code = playerToParty.get(leader.getUniqueId());

        if (code == null) {
            return false;
        }

        Party party = parties.get(code);

        if (party == null || !party.getLeader().equals(leader.getUniqueId())) {
            return false;
        }

        String instanceId = mapManager.createPartyMap(mapName, code);
        party.setCurrentMap(instanceId);

        // Wait for map to be ready, then teleport all members
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (UUID memberId : party.getMembers()) {
                Player member = plugin.getServer().getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    mapManager.joinMap(member, instanceId);
                }
            }
        }, 100L); // 5 second delay

        return true;
    }

    public Party getParty(String code) {
        return parties.get(code);
    }

    public String getPlayerParty(UUID playerId) {
        return playerToParty.get(playerId);
    }

    private void notifyParty(Party party, String message) {
        for (UUID memberId : party.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    private String generatePartyCode() {
        String code;
        do {
            code = String.format("%04d", new Random().nextInt(10000));
        } while (parties.containsKey(code));
        return code;
    }

    public void invitePlayer(Player sender, Player target) {
        String partyCode = getPlayerParty(sender.getUniqueId());
        if (partyCode == null) {
            sender.sendMessage(ChatColor.RED + "You are not in a party! Create one first.");
            return;
        }

        Party party = getParty(partyCode);
        if (!party.getLeader().equals(sender.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "Only the party leader can invite players.");
            return;
        }

        if (getPlayerParty(target.getUniqueId()) != null) {
            sender.sendMessage(ChatColor.RED + target.getName() + " is already in a party.");
            return;
        }

        invites.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(sender.getUniqueId());

        sender.sendMessage(ChatColor.GREEN + "Invited " + target.getName() + " to the party!");
        target.sendMessage(ChatColor.GREEN + sender.getName() + " invited you to their party! Type " + ChatColor.YELLOW
                + "/party accept " + sender.getName() + ChatColor.GREEN + " to join.");

        // Expire after 60s
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (invites.containsKey(target.getUniqueId())) {
                Set<UUID> senders = invites.get(target.getUniqueId());
                if (senders != null && senders.remove(sender.getUniqueId())) {
                    if (sender.isOnline())
                        sender.sendMessage(ChatColor.YELLOW + "Invite to " + target.getName() + " expired.");
                    if (target.isOnline())
                        target.sendMessage(ChatColor.YELLOW + "Invite from " + sender.getName() + " expired.");
                }
            }
        }, 1200L);
    }

    public void acceptInvite(Player player, String senderName) {
        Player sender = Bukkit.getPlayer(senderName);
        if (sender == null) {
            player.sendMessage(ChatColor.RED + "Player not found or offline.");
            return;
        }

        if (!invites.containsKey(player.getUniqueId())
                || !invites.get(player.getUniqueId()).contains(sender.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You don't have an invite from " + senderName);
            return;
        }

        String partyCode = getPlayerParty(sender.getUniqueId());
        if (partyCode == null) {
            player.sendMessage(ChatColor.RED + "The party no longer exists.");
            invites.get(player.getUniqueId()).remove(sender.getUniqueId());
            return;
        }

        invites.get(player.getUniqueId()).remove(sender.getUniqueId());
        joinParty(player, partyCode);
    }

    public void requestJoin(Player requester, Player target) {
        if (getPlayerParty(requester.getUniqueId()) != null) {
            requester.sendMessage(ChatColor.RED + "You are already in a party.");
            return;
        }

        String partyCode = getPlayerParty(target.getUniqueId());
        if (partyCode == null) {
            requester.sendMessage(ChatColor.RED + target.getName() + " is not in a party.");
            return;
        }

        if (!friendManager.areFriends(requester.getUniqueId(), target.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + "You can only request to join friends' parties.");
            return;
        }

        joinRequests.computeIfAbsent(target.getUniqueId(), k -> new HashSet<>()).add(requester.getUniqueId());
        requester.sendMessage(ChatColor.GREEN + "Sent join request to " + target.getName());
        target.sendMessage(ChatColor.GREEN + requester.getName() + " wants to join your party! Type " + ChatColor.YELLOW
                + "/party acceptrequest " + requester.getName() + ChatColor.GREEN + " to accept.");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (joinRequests.containsKey(target.getUniqueId())) {
                Set<UUID> requesters = joinRequests.get(target.getUniqueId());
                if (requesters != null && requesters.remove(requester.getUniqueId())) {
                    if (requester.isOnline())
                        requester.sendMessage(ChatColor.YELLOW + "Join request to " + target.getName() + " expired.");
                }
            }
        }, 1200L);
    }

    public void acceptJoinRequest(Player target, String requesterName) {
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester == null) {
            target.sendMessage(ChatColor.RED + "Player not found or offline.");
            return;
        }

        if (!joinRequests.containsKey(target.getUniqueId())
                || !joinRequests.get(target.getUniqueId()).contains(requester.getUniqueId())) {
            target.sendMessage(ChatColor.RED + "No join request from " + requesterName);
            return;
        }

        String partyCode = getPlayerParty(target.getUniqueId());
        if (partyCode == null) {
            target.sendMessage(ChatColor.RED + "You are not in a party.");
            return;
        }

        Party party = getParty(partyCode);
        if (!party.getLeader().equals(target.getUniqueId())) {
            target.sendMessage(ChatColor.RED + "Only the party leader can accept join requests.");
            return;
        }

        joinRequests.get(target.getUniqueId()).remove(requester.getUniqueId());
        joinParty(requester, partyCode);
    }

    public void togglePartyChat(Player player) {
        if (partyChatToggled.contains(player.getUniqueId())) {
            partyChatToggled.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Party chat disabled.");
        } else {
            partyChatToggled.add(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Party chat enabled.");
        }
    }

    public boolean isPartyChatEnabled(Player player) {
        return partyChatToggled.contains(player.getUniqueId());
    }

    public void sendPartyChat(Player sender, String message) {
        String partyCode = getPlayerParty(sender.getUniqueId());
        if (partyCode == null) {
            sender.sendMessage(ChatColor.RED + "You are not in a party.");
            partyChatToggled.remove(sender.getUniqueId());
            return;
        }

        Party party = getParty(partyCode);
        String format = ChatColor.BLUE + "[Party] " + ChatColor.WHITE + sender.getName() + ": " + message;
        notifyParty(party, format);
    }
}