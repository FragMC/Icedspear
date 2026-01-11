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
    private final Map<String, Party> parties;
    private final Map<UUID, String> playerToParty;
    private final Map<UUID, Set<UUID>> partyInvites; // Player -> Set of party codes (as leader UUIDs)

    public PartyManager(IcedSpear plugin, MapManager mapManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.configManager = configManager;
        this.parties = new ConcurrentHashMap<>();
        this.playerToParty = new ConcurrentHashMap<>();
        this.partyInvites = new ConcurrentHashMap<>();
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

    /**
     * Invite a friend to the party
     * @param leader Party leader
     * @param friendName Friend to invite
     * @return true if invite sent
     */
    public boolean inviteFriend(Player leader, String friendName) {
        String code = playerToParty.get(leader.getUniqueId());
        if (code == null) {
            leader.sendMessage(ChatColor.RED + "You're not in a party!");
            return false;
        }

        Party party = parties.get(code);
        if (party == null || !party.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage(ChatColor.RED + "Only the party leader can invite players!");
            return false;
        }

        Player friend = Bukkit.getPlayer(friendName);
        if (friend == null) {
            leader.sendMessage(ChatColor.RED + "Player not found!");
            return false;
        }

        if (playerToParty.containsKey(friend.getUniqueId())) {
            leader.sendMessage(ChatColor.RED + friend.getName() + " is already in a party!");
            return false;
        }

        partyInvites.computeIfAbsent(friend.getUniqueId(), k -> new HashSet<>()).add(leader.getUniqueId());

        leader.sendMessage(ChatColor.GREEN + "Invited " + friend.getName() + " to the party!");
        friend.sendMessage(ChatColor.YELLOW + leader.getName() + " invited you to their party!");
        friend.sendMessage(ChatColor.GRAY + "Use /party accept " + leader.getName() + " to accept.");

        return true;
    }

    /**
     * Accept a party invite
     * @param player Player accepting
     * @param inviterName Name of inviter
     * @return true if accepted
     */
    public boolean acceptInvite(Player player, String inviterName) {
        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter == null) {
            player.sendMessage(ChatColor.RED + "Player not found!");
            return false;
        }

        Set<UUID> invites = partyInvites.get(player.getUniqueId());
        if (invites == null || !invites.contains(inviter.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "No party invite from this player!");
            return false;
        }

        invites.remove(inviter.getUniqueId());

        String code = playerToParty.get(inviter.getUniqueId());
        if (code == null) {
            player.sendMessage(ChatColor.RED + "Party no longer exists!");
            return false;
        }

        return joinParty(player, code);
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

        broadcastToParty(party, ChatColor.GREEN + player.getName() + " joined the party!");

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
                disbandParty(code);
            } else {
                broadcastToParty(party, ChatColor.YELLOW + player.getName() + " left the party.");
            }
        }
    }

    public boolean kickMember(Player leader, String targetName) {
        String code = playerToParty.get(leader.getUniqueId());
        if (code == null) return false;

        Party party = parties.get(code);
        if (party == null || !party.getLeader().equals(leader.getUniqueId())) return false;

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) return false;

        if (!party.getMembers().contains(target.getUniqueId())) return false;
        if (target.getUniqueId().equals(leader.getUniqueId())) return false;

        party.removeMember(target.getUniqueId());
        playerToParty.remove(target.getUniqueId());

        target.sendMessage(ChatColor.RED + "You have been kicked from the party.");
        broadcastToParty(party, ChatColor.YELLOW + target.getName() + " has been kicked from the party.");

        return true;
    }

    public void disbandParty(String code) {
        Party party = parties.remove(code);

        if (party != null) {
            for (UUID memberId : party.getMembers()) {
                playerToParty.remove(memberId);
            }

            broadcastToParty(party, ChatColor.RED + "Party has been disbanded.");
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

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (UUID memberId : party.getMembers()) {
                Player member = plugin.getServer().getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    mapManager.joinMap(member, instanceId);
                }
            }
        }, 100L);

        return true;
    }

    /**
     * Send a message to party chat
     * @param player Player sending message
     * @param message Message content
     * @return true if sent
     */
    public boolean sendPartyChat(Player player, String message) {
        String code = playerToParty.get(player.getUniqueId());
        if (code == null) {
            player.sendMessage(ChatColor.RED + "You're not in a party!");
            return false;
        }

        Party party = parties.get(code);
        if (party == null) return false;

        String formatted = ChatColor.LIGHT_PURPLE + "[Party] " + ChatColor.WHITE + player.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + message;
        broadcastToParty(party, formatted);

        return true;
    }

    public void broadcastToParty(Party party, String message) {
        for (UUID memberId : party.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    public Party getParty(String code) {
        return parties.get(code);
    }

    public String getPlayerParty(UUID playerId) {
        return playerToParty.get(playerId);
    }

    public Set<UUID> getPartyInvites(UUID playerId) {
        return new HashSet<>(partyInvites.getOrDefault(playerId, new HashSet<>()));
    }

    private String generatePartyCode() {
        String code;
        do {
            code = String.format("%04d", new Random().nextInt(10000));
        } while (parties.containsKey(code));
        return code;
    }
}