package com.stufy.fragmc.icedspear.managers;

import com.stufy.fragmc.icedspear.IcedSpear;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class FriendManager {
    private final IcedSpear plugin;
    private final File friendsFile;
    private FileConfiguration friendsConfig;
    private final Map<UUID, Set<UUID>> friendships;
    private final Map<UUID, Set<UUID>> pendingRequests;
    private final Map<UUID, Set<UUID>> joinRequests; // Friend -> Set of players requesting to join

    public FriendManager(IcedSpear plugin) {
        this.plugin = plugin;
        this.friendsFile = new File(plugin.getDataFolder(), "friends.yml");
        this.friendships = new HashMap<>();
        this.pendingRequests = new HashMap<>();
        this.joinRequests = new HashMap<>();

        loadFriends();
    }

    private void loadFriends() {
        if (!friendsFile.exists()) {
            try {
                friendsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create friends.yml");
            }
        }

        friendsConfig = YamlConfiguration.loadConfiguration(friendsFile);

        if (friendsConfig.contains("friendships")) {
            for (String key : friendsConfig.getConfigurationSection("friendships").getKeys(false)) {
                UUID playerId = UUID.fromString(key);
                List<String> friendIds = friendsConfig.getStringList("friendships." + key);

                Set<UUID> friends = new HashSet<>();
                for (String friendId : friendIds) {
                    friends.add(UUID.fromString(friendId));
                }

                friendships.put(playerId, friends);
            }
        }
    }

    private void saveFriends() {
        for (Map.Entry<UUID, Set<UUID>> entry : friendships.entrySet()) {
            List<String> friendIds = new ArrayList<>();
            for (UUID friendId : entry.getValue()) {
                friendIds.add(friendId.toString());
            }
            friendsConfig.set("friendships." + entry.getKey().toString(), friendIds);
        }

        try {
            friendsConfig.save(friendsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save friends.yml");
        }
    }

    public boolean sendFriendRequest(Player sender, Player target) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();

        if (areFriends(senderId, targetId)) {
            return false;
        }

        if (hasPendingRequest(targetId, senderId)) {
            // Auto-accept if target already sent a request
            acceptFriendRequest(target, sender);
            return true;
        }

        pendingRequests.computeIfAbsent(targetId, k -> new HashSet<>()).add(senderId);
        return true;
    }

    public boolean acceptFriendRequest(Player accepter, Player requester) {
        UUID accepterId = accepter.getUniqueId();
        UUID requesterId = requester.getUniqueId();

        Set<UUID> requests = pendingRequests.get(accepterId);
        if (requests == null || !requests.contains(requesterId)) {
            return false;
        }

        requests.remove(requesterId);

        friendships.computeIfAbsent(accepterId, k -> new HashSet<>()).add(requesterId);
        friendships.computeIfAbsent(requesterId, k -> new HashSet<>()).add(accepterId);

        saveFriends();
        return true;
    }

    public boolean removeFriend(Player player, UUID friendId) {
        UUID playerId = player.getUniqueId();

        Set<UUID> playerFriends = friendships.get(playerId);
        Set<UUID> friendFriends = friendships.get(friendId);

        if (playerFriends == null || !playerFriends.contains(friendId)) {
            return false;
        }

        playerFriends.remove(friendId);
        if (friendFriends != null) {
            friendFriends.remove(playerId);
        }

        saveFriends();
        return true;
    }

    // ===== JOIN REQUEST SYSTEM =====

    /**
     * Send a request to join a friend's map
     * @param requester Player requesting to join
     * @param friend Friend whose map to join
     * @return true if request was sent
     */
    public boolean sendJoinRequest(Player requester, Player friend) {
        if (!areFriends(requester.getUniqueId(), friend.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + "You can only request to join friends!");
            return false;
        }

        // Check if friend is in a map
        String instanceId = plugin.getMapManager().getPlayerInstance(friend.getUniqueId());
        if (instanceId == null) {
            requester.sendMessage(ChatColor.RED + friend.getName() + " is not in a map!");
            return false;
        }

        joinRequests.computeIfAbsent(friend.getUniqueId(), k -> new HashSet<>()).add(requester.getUniqueId());

        requester.sendMessage(ChatColor.GREEN + "Join request sent to " + friend.getName() + "!");
        friend.sendMessage(ChatColor.YELLOW + requester.getName() + " wants to join your map!");
        friend.sendMessage(ChatColor.GRAY + "Use /friend acceptjoin " + requester.getName() + " to accept.");

        return true;
    }

    /**
     * Accept a join request
     * @param accepter Player accepting the request
     * @param requesterName Name of requester
     * @return true if accepted
     */
    public boolean acceptJoinRequest(Player accepter, String requesterName) {
        Player requester = Bukkit.getPlayer(requesterName);
        if (requester == null) {
            accepter.sendMessage(ChatColor.RED + "Player not found!");
            return false;
        }

        Set<UUID> requests = joinRequests.get(accepter.getUniqueId());
        if (requests == null || !requests.contains(requester.getUniqueId())) {
            accepter.sendMessage(ChatColor.RED + "No join request from this player!");
            return false;
        }

        requests.remove(requester.getUniqueId());

        // Get the map instance and invite player
        String instanceId = plugin.getMapManager().getPlayerInstance(accepter.getUniqueId());
        if (instanceId == null) {
            accepter.sendMessage(ChatColor.RED + "You are no longer in a map!");
            return false;
        }

        boolean success = plugin.getMapManager().joinMap(requester, instanceId);
        if (success) {
            accepter.sendMessage(ChatColor.GREEN + requester.getName() + " is joining your map!");
        } else {
            accepter.sendMessage(ChatColor.RED + "Failed to add " + requester.getName() + " to your map!");
        }

        return success;
    }

    /**
     * Get pending join requests for a player
     * @param playerId Player UUID
     * @return Set of requester UUIDs
     */
    public Set<UUID> getJoinRequests(UUID playerId) {
        return new HashSet<>(joinRequests.getOrDefault(playerId, new HashSet<>()));
    }

    public Set<UUID> getFriends(UUID playerId) {
        return new HashSet<>(friendships.getOrDefault(playerId, new HashSet<>()));
    }

    public Set<UUID> getPendingRequests(UUID playerId) {
        return new HashSet<>(pendingRequests.getOrDefault(playerId, new HashSet<>()));
    }

    public boolean areFriends(UUID player1, UUID player2) {
        Set<UUID> friends = friendships.get(player1);
        return friends != null && friends.contains(player2);
    }

    public boolean hasPendingRequest(UUID targetId, UUID senderId) {
        Set<UUID> requests = pendingRequests.get(targetId);
        return requests != null && requests.contains(senderId);
    }
}