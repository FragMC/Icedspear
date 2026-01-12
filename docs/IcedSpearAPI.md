\# IcedSpear API Documentation



Version 1.3.0



\## Table of Contents

1\. \[Introduction](#introduction)

2\. \[Getting Started](#getting-started)

3\. \[API Access](#api-access)

4\. \[Map API](#map-api)

5\. \[Party API](#party-api)

6\. \[Friend API](#friend-api)

7\. \[Schematic API](#schematic-api)

8\. \[Config API](#config-api)

9\. \[Events](#events)

10\. \[Example Addon](#example-addon)



---



\## Introduction



The IcedSpear API allows developers to create addons that interact with IcedSpear's map, party, and friend systems. Addons are separate plugins that depend on IcedSpear.



\### What Can You Do With This API?



\- Create custom map selection GUIs

\- Build leaderboards for specific maps

\- Create matchmaking systems

\- Add custom party features

\- Integrate with other plugins (economy, ranks, etc.)

\- Track player statistics across maps

\- Create map voting systems



---



\## Getting Started



\### Prerequisites



\- Java 21+

\- IcedSpear plugin installed on server

\- Paper/Spigot 1.21.4+



\### Adding IcedSpear as a Dependency



\#### Maven



```xml

<dependencies>

&nbsp;   <dependency>

&nbsp;       <groupId>com.stufy.fragmc</groupId>

&nbsp;       <artifactId>icedspear</artifactId>

&nbsp;       <version>1.2.0</version>

&nbsp;       <scope>provided</scope>

&nbsp;   </dependency>

</dependencies>

```



\#### Gradle



```gradle

dependencies {

&nbsp;   compileOnly 'com.stufy.fragmc:icedspear:1.2.0'

}

```



\### plugin.yml Configuration



```yaml

name: YourAddon

version: 1.0.0

main: com.yourname.youraddon.YourAddon

depend: \[IcedSpear]

api-version: 1.21

```



---



\## API Access



\### Method 1: Services Manager (Recommended)



```java

import com.stufy.fragmc.icedspear.api.IcedSpearAPI;

import org.bukkit.Bukkit;

import org.bukkit.plugin.RegisteredServiceProvider;



public class YourAddon extends JavaPlugin {

&nbsp;   private IcedSpearAPI icedspear;



&nbsp;   @Override

&nbsp;   public void onEnable() {

&nbsp;       if (!setupIcedSpear()) {

&nbsp;           getLogger().severe("IcedSpear not found! Disabling...");

&nbsp;           getServer().getPluginManager().disablePlugin(this);

&nbsp;           return;

&nbsp;       }

&nbsp;       

&nbsp;       getLogger().info("Successfully hooked into IcedSpear!");

&nbsp;   }



&nbsp;   private boolean setupIcedSpear() {

&nbsp;       RegisteredServiceProvider<IcedSpearAPI> provider = 

&nbsp;           Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class);

&nbsp;       

&nbsp;       if (provider != null) {

&nbsp;           icedspear = provider.getProvider();

&nbsp;           return true;

&nbsp;       }

&nbsp;       return false;

&nbsp;   }



&nbsp;   public IcedSpearAPI getIcedSpear() {

&nbsp;       return icedspear;

&nbsp;   }

}

```



\### Method 2: Direct Plugin Access



```java

import com.stufy.fragmc.icedspear.IcedSpear;

import org.bukkit.Bukkit;



Plugin plugin = Bukkit.getPluginManager().getPlugin("IcedSpear");

if (plugin instanceof IcedSpear) {

&nbsp;   IcedSpear icedspear = (IcedSpear) plugin;

&nbsp;   IcedSpearAPI api = icedspear.getAPI();

}

```



---



\## Map API



\### Get Available Maps



```java

Set<String> maps = api.getAvailableMaps();

for (String mapName : maps) {

&nbsp;   System.out.println("Available map: " + mapName);

}

```



\### Get Active Map Instances



```java

Map<String, MapInstance> instances = api.getActiveMapInstances();



for (Map.Entry<String, MapInstance> entry : instances.entrySet()) {

&nbsp;   String instanceId = entry.getKey();

&nbsp;   MapInstance instance = entry.getValue();

&nbsp;   

&nbsp;   System.out.println("Instance: " + instanceId);

&nbsp;   System.out.println("Map: " + instance.getMapName());

&nbsp;   System.out.println("Players: " + instance.getPlayers().size());

&nbsp;   System.out.println("Public: " + instance.isPublic());

&nbsp;   System.out.println("State: " + instance.getState());

}

```



\### Get Player's Current Map



```java

MapInstance instance = api.getPlayerMapInstance(player);

if (instance != null) {

&nbsp;   player.sendMessage("You are in: " + instance.getMapName());

} else {

&nbsp;   player.sendMessage("You are not in any map");

}

```



\### Create Map Instances



```java

// Create public map

String instanceId = api.createPublicMap("skyblock");



// Create private map

String privateId = api.createPrivateMap("pvp\_arena");



// Join a map

boolean success = api.joinMap(player, instanceId);

if (success) {

&nbsp;   player.sendMessage("Joining map...");

}



// Leave map

api.leaveMap(player);

```



\### Check Map Permissions



```java

boolean canJoin = api.canPlayerJoinMap(player, "skyblock");

if (!canJoin) {

&nbsp;   player.sendMessage("You don't have permission for this map!");

}

```



\### MapInstance Object



```java

MapInstance instance = api.getMapInstance(instanceId);



// Get information

String mapName = instance.getMapName();

boolean isPublic = instance.isPublic();

MapState state = instance.getState();

Set<UUID> players = instance.getPlayers();

Location spawn = instance.getSpawnLocation();

World world = instance.getWorld();

long createdAt = instance.getCreatedAt();



// MapState enum values:

// CREATING, WAITING, RUNNING, ENDING, DESTROYING, ERROR

```



---



\## Party API



\### Get Party Information



```java

// Get player's party

Party party = api.getPlayerParty(player);

if (party != null) {

&nbsp;   UUID leader = party.getLeader();

&nbsp;   Set<UUID> members = party.getMembers();

&nbsp;   String code = party.getCode();

&nbsp;   String currentMap = party.getCurrentMap();

}



// Get party by code

Party party = api.getParty("1234");

```



\### Manage Parties



```java

// Create party

String partyCode = api.createParty(player);

if (partyCode != null) {

&nbsp;   player.sendMessage("Party created: " + partyCode);

}



// Join party

boolean joined = api.joinParty(player, "1234");



// Leave party

api.leaveParty(player);



// Send party message

api.sendPartyMessage("1234", ChatColor.GOLD + "Server announcement!");

```



\### Party Object



```java

Party party = api.getParty(code);



String code = party.getCode();

UUID leaderId = party.getLeader();

Set<UUID> memberIds = party.getMembers();

String mapInstanceId = party.getCurrentMap();



// Check if player is leader

boolean isLeader = party.getLeader().equals(player.getUniqueId());



// Get member count

int size = party.getMembers().size();

```



---



\## Friend API



\### Friend Management



```java

// Get friends

Set<UUID> friends = api.getFriends(player);



// Check friendship

boolean areFriends = api.areFriends(player1, player2);



// Send friend request

boolean sent = api.sendFriendRequest(sender, target);



// Get pending requests

Set<UUID> requests = api.getPendingFriendRequests(player);

```



\### Example: Friend List GUI



```java

public void openFriendList(Player player) {

&nbsp;   Set<UUID> friends = api.getFriends(player);

&nbsp;   

&nbsp;   Inventory inv = Bukkit.createInventory(null, 54, "Friends");

&nbsp;   

&nbsp;   int slot = 0;

&nbsp;   for (UUID friendId : friends) {

&nbsp;       OfflinePlayer friend = Bukkit.getOfflinePlayer(friendId);

&nbsp;       

&nbsp;       ItemStack item = new ItemStack(Material.PLAYER\_HEAD);

&nbsp;       ItemMeta meta = item.getItemMeta();

&nbsp;       meta.setDisplayName(ChatColor.GREEN + friend.getName());

&nbsp;       

&nbsp;       List<String> lore = new ArrayList<>();

&nbsp;       if (friend.isOnline()) {

&nbsp;           lore.add(ChatColor.GREEN + "● Online");

&nbsp;           

&nbsp;           MapInstance map = api.getPlayerMapInstance((Player) friend);

&nbsp;           if (map != null) {

&nbsp;               lore.add(ChatColor.GRAY + "Playing: " + map.getMapName());

&nbsp;           }

&nbsp;       } else {

&nbsp;           lore.add(ChatColor.GRAY + "● Offline");

&nbsp;       }

&nbsp;       

&nbsp;       meta.setLore(lore);

&nbsp;       item.setItemMeta(meta);

&nbsp;       

&nbsp;       inv.setItem(slot++, item);

&nbsp;   }

&nbsp;   

&nbsp;   player.openInventory(inv);

}

```



---



\## Schematic API



\### Get Schematics



```java

// Get schematic for a map

String schematicName = api.getSchematicForMap("skyblock");



// Get all map-schematic mappings

Map<String, String> mappings = api.getAllMapSchematics();



for (Map.Entry<String, String> entry : mappings.entrySet()) {

&nbsp;   System.out.println("Map: " + entry.getKey());

&nbsp;   System.out.println("Schematic: " + entry.getValue());

}

```



---



\## Config API



\### Get Configuration Values



```java

// Get max players

int maxPlayers = api.getMaxPlayers();



// Get cleanup delay

long delay = api.getCleanupDelay();



// Check if map is globally blocked

boolean blocked = api.isMapGloballyBlocked("pvp\_arena");

```



---



\## Events



IcedSpear fires custom events that addons can listen to.



\### Available Events



```java

// Map events

PlayerJoinMapEvent

PlayerLeaveMapEvent

MapInstanceCreateEvent

MapInstanceDestroyEvent



// Party events

PartyCreateEvent

PlayerJoinPartyEvent

PlayerLeavePartyEvent

```



\### Example Event Listener



```java

import com.stufy.fragmc.icedspear.api.events.\*;

import org.bukkit.event.EventHandler;

import org.bukkit.event.Listener;



public class MapListener implements Listener {

&nbsp;   

&nbsp;   @EventHandler

&nbsp;   public void onPlayerJoinMap(PlayerJoinMapEvent event) {

&nbsp;       Player player = event.getPlayer();

&nbsp;       String mapName = event.getMapName();

&nbsp;       String instanceId = event.getInstanceId();

&nbsp;       

&nbsp;       player.sendMessage("Welcome to " + mapName + "!");

&nbsp;       

&nbsp;       // You can cancel the event

&nbsp;       // event.setCancelled(true);

&nbsp;   }

&nbsp;   

&nbsp;   @EventHandler

&nbsp;   public void onPlayerLeaveMap(PlayerLeaveMapEvent event) {

&nbsp;       Player player = event.getPlayer();

&nbsp;       String mapName = event.getMapName();

&nbsp;       

&nbsp;       // Track statistics, save data, etc.

&nbsp;   }

&nbsp;   

&nbsp;   @EventHandler

&nbsp;   public void onMapCreate(MapInstanceCreateEvent event) {

&nbsp;       String instanceId = event.getInstanceId();

&nbsp;       String mapName = event.getMapName();

&nbsp;       boolean isPublic = event.isPublic();

&nbsp;       

&nbsp;       System.out.println("New map created: " + mapName);

&nbsp;   }

&nbsp;   

&nbsp;   @EventHandler

&nbsp;   public void onPartyCreate(PartyCreateEvent event) {

&nbsp;       String code = event.getPartyCode();

&nbsp;       Player leader = event.getLeader();

&nbsp;       

&nbsp;       leader.sendMessage("Party system initialized!");

&nbsp;   }

}

```



\### Register Event Listener



```java

@Override

public void onEnable() {

&nbsp;   getServer().getPluginManager().registerEvents(new MapListener(), this);

}

```



---



\## Example Addon



\### Complete Example: Map Statistics Tracker



```java

package com.example.mapstats;



import com.stufy.fragmc.icedspear.api.IcedSpearAPI;

import com.stufy.fragmc.icedspear.api.events.\*;

import com.stufy.fragmc.icedspear.models.MapInstance;

import org.bukkit.Bukkit;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;

import org.bukkit.event.Listener;

import org.bukkit.plugin.RegisteredServiceProvider;

import org.bukkit.plugin.java.JavaPlugin;



import java.util.HashMap;

import java.util.Map;

import java.util.UUID;



public class MapStatsAddon extends JavaPlugin implements Listener {

&nbsp;   private IcedSpearAPI api;

&nbsp;   private Map<UUID, Long> joinTimes = new HashMap<>();

&nbsp;   private Map<UUID, Integer> playCount = new HashMap<>();

&nbsp;   

&nbsp;   @Override

&nbsp;   public void onEnable() {

&nbsp;       if (!setupAPI()) {

&nbsp;           getLogger().severe("IcedSpear not found!");

&nbsp;           getServer().getPluginManager().disablePlugin(this);

&nbsp;           return;

&nbsp;       }

&nbsp;       

&nbsp;       getServer().getPluginManager().registerEvents(this, this);

&nbsp;       getCommand("mapstats").setExecutor(new StatsCommand(this));

&nbsp;       

&nbsp;       getLogger().info("MapStats addon enabled!");

&nbsp;   }

&nbsp;   

&nbsp;   private boolean setupAPI() {

&nbsp;       RegisteredServiceProvider<IcedSpearAPI> provider = 

&nbsp;           Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class);

&nbsp;       

&nbsp;       if (provider != null) {

&nbsp;           api = provider.getProvider();

&nbsp;           return true;

&nbsp;       }

&nbsp;       return false;

&nbsp;   }

&nbsp;   

&nbsp;   @EventHandler

&nbsp;   public void onPlayerJoinMap(PlayerJoinMapEvent event) {

&nbsp;       Player player = event.getPlayer();

&nbsp;       

&nbsp;       // Track join time

&nbsp;       joinTimes.put(player.getUniqueId(), System.currentTimeMillis());

&nbsp;       

&nbsp;       // Increment play count

&nbsp;       playCount.merge(player.getUniqueId(), 1, Integer::sum);

&nbsp;   }

&nbsp;   

&nbsp;   @EventHandler

&nbsp;   public void onPlayerLeaveMap(PlayerLeaveMapEvent event) {

&nbsp;       Player player = event.getPlayer();

&nbsp;       UUID playerId = player.getUniqueId();

&nbsp;       

&nbsp;       if (joinTimes.containsKey(playerId)) {

&nbsp;           long timeSpent = System.currentTimeMillis() - joinTimes.get(playerId);

&nbsp;           long minutes = timeSpent / 60000;

&nbsp;           

&nbsp;           player.sendMessage("You played for " + minutes + " minutes!");

&nbsp;           joinTimes.remove(playerId);

&nbsp;       }

&nbsp;   }

&nbsp;   

&nbsp;   public int getPlayCount(UUID playerId) {

&nbsp;       return playCount.getOrDefault(playerId, 0);

&nbsp;   }

&nbsp;   

&nbsp;   public IcedSpearAPI getAPI() {

&nbsp;       return api;

&nbsp;   }

}



// StatsCommand.java

class StatsCommand implements CommandExecutor {

&nbsp;   private final MapStatsAddon plugin;

&nbsp;   

&nbsp;   public StatsCommand(MapStatsAddon plugin) {

&nbsp;       this.plugin = plugin;

&nbsp;   }

&nbsp;   

&nbsp;   @Override

&nbsp;   public boolean onCommand(CommandSender sender, Command cmd, String label, String\[] args) {

&nbsp;       if (!(sender instanceof Player)) return true;

&nbsp;       Player player = (Player) sender;

&nbsp;       

&nbsp;       IcedSpearAPI api = plugin.getAPI();

&nbsp;       

&nbsp;       player.sendMessage(ChatColor.GOLD + "=== Your Map Stats ===");

&nbsp;       player.sendMessage("Maps Played: " + plugin.getPlayCount(player.getUniqueId()));

&nbsp;       

&nbsp;       MapInstance current = api.getPlayerMapInstance(player);

&nbsp;       if (current != null) {

&nbsp;           player.sendMessage("Current Map: " + current.getMapName());

&nbsp;       }

&nbsp;       

&nbsp;       return true;

&nbsp;   }

}

```



---



\## Best Practices



1\. \*\*Always check for null values\*\* when getting map instances, parties, etc.

2\. \*\*Use the API through Services Manager\*\* for better compatibility

3\. \*\*Listen to events\*\* instead of polling for changes

4\. \*\*Cache data when appropriate\*\* but refresh when needed

5\. \*\*Handle plugin reload gracefully\*\* - check if IcedSpear is still enabled

6\. \*\*Don't modify internal objects\*\* - use API methods instead



\## Support



For issues, questions, or feature requests:

\- GitHub: \[Your Repository]

\- Discord: \[Your Server]



---



\*\*Happy coding!\*\* 🎮

