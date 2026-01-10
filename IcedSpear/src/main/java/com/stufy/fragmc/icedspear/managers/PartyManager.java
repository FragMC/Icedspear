package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.models.Party;
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

    public PartyManager(IcedSpear plugin, MapManager mapManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.configManager = configManager;
        this.parties = new ConcurrentHashMap<>();
        this.playerToParty = new ConcurrentHashMap<>();
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
}