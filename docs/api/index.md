# IcedSpear API Documentation

Version 1.3.0

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
3. [API Access](#api-access)
4. [Map API](#map-api)
5. [Party API](#party-api)
6. [Friend API](#friend-api)
7. [Schematic API](#schematic-api)
8. [Config API](#config-api)
9. [Events](#events)
10. [Example Addon](#example-addon)

---

## Introduction

The IcedSpear API allows developers to create addons that interact with IcedSpear's map, party, and friend systems. Addons are separate plugins that depend on IcedSpear.

### What Can You Do With This API?

- Create custom map selection GUIs
- Build leaderboards for specific maps
- Create matchmaking systems
- Add custom party features
- Integrate with other plugins (economy, ranks, etc.)
- Track player statistics across maps
- Create map voting systems

---

## Getting Started

### Prerequisites

- Java 21+
- IcedSpear plugin installed on server
- Paper/Spigot 1.21.4+

### Adding IcedSpear as a Dependency

#### Maven

```xml
<dependencies>
    <dependency>
        <groupId>com.stufy.fragmc</groupId>
        <artifactId>icedspear</artifactId>
        <version>1.2.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

#### Gradle

```groovy
dependencies {
    compileOnly 'com.stufy.fragmc:icedspear:1.2.0'
}
```

### plugin.yml Configuration

```yaml
name: YourAddon
version: 1.0.0
main: com.yourname.youraddon.YourAddon
depend: [IcedSpear]
api-version: 1.21
```

---

## API Access

### Method 1: Services Manager (Recommended)

```java
import com.stufy.fragmc.icedspear.api.IcedSpearAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class YourAddon extends JavaPlugin {
    private IcedSpearAPI icedspear;

    @Override
    public void onEnable() {
        if (!setupIcedSpear()) {
            getLogger().severe("IcedSpear not found! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("Successfully hooked into IcedSpear!");
    }

    private boolean setupIcedSpear() {
        RegisteredServiceProvider<IcedSpearAPI> provider = 
            Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class);
        
        if (provider != null) {
            icedspear = provider.getProvider();
            return true;
        }
        return false;
    }

    public IcedSpearAPI getIcedSpear() {
        return icedspear;
    }
}
```

### Method 2: Direct Plugin Access

```java
import com.stufy.fragmc.icedspear.IcedSpear;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

Plugin plugin = Bukkit.getPluginManager().getPlugin("IcedSpear");
if (plugin instanceof IcedSpear) {
    IcedSpear icedspear = (IcedSpear) plugin;
    IcedSpearAPI api = icedspear.getAPI();
}
```

---

## Map API

### Get Available Maps

```java
Set<String> maps = api.getAvailableMaps();
for (String mapName : maps) {
    System.out.println("Available map: " + mapName);
}
```

### Get Active Map Instances

```java
Map<String, MapInstance> instances = api.getActiveMapInstances();

for (Map.Entry<String, MapInstance> entry : instances.entrySet()) {
    String instanceId = entry.getKey();
    MapInstance instance = entry.getValue();
    
    System.out.println("Instance: " + instanceId);
    System.out.println("Map: " + instance.getMapName());
    System.out.println("Players: " + instance.getPlayers().size());
    System.out.println("Public: " + instance.isPublic());
    System.out.println("State: " + instance.getState());
}
```

### Get Player's Current Map

```java
MapInstance instance = api.getPlayerMapInstance(player);
if (instance != null) {
    player.sendMessage("You are in: " + instance.getMapName());
} else {
    player.sendMessage("You are not in any map");
}
```

### Create Map Instances

```java
// Create public map
String instanceId = api.createPublicMap("skyblock");

// Create private map
String privateId = api.createPrivateMap("pvp_arena");

// Join a map
boolean success = api.joinMap(player, instanceId);
if (success) {
    player.sendMessage("Joining map...");
}

// Leave map
api.leaveMap(player);
```

### Check Map Permissions

```java
boolean canJoin = api.canPlayerJoinMap(player, "skyblock");
if (!canJoin) {
    player.sendMessage("You don't have permission for this map!");
}
```

### MapInstance Object

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

## Party API

### Get Party Information

```java
// Get player's party
Party party = api.getPlayerParty(player);
if (party != null) {
    UUID leader = party.getLeader();
    Set<UUID> members = party.getMembers();
    String code = party.getCode();
    String currentMap = party.getCurrentMap();
}

// Get party by code
Party party = api.getParty("1234");
```

### Manage Parties

```java
// Create party
String partyCode = api.createParty(player);
if (partyCode != null) {
    player.sendMessage("Party created: " + partyCode);
}

// Join party
boolean joined = api.joinParty(player, "1234");

// Leave party
api.leaveParty(player);

// Send party message
api.sendPartyMessage("1234", ChatColor.GOLD + "Server announcement!");
```

### Party Object

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

## Friend API

### Friend Management

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

### Example: Friend List GUI

```java
public void openFriendList(Player player) {
    Set<UUID> friends = api.getFriends(player);
    
    Inventory inv = Bukkit.createInventory(null, 54, "Friends");
    
    int slot = 0;
    for (UUID friendId : friends) {
        OfflinePlayer friend = Bukkit.getOfflinePlayer(friendId);
        
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + friend.getName());
        
        List<String> lore = new ArrayList<>();
        if (friend.isOnline()) {
            lore.add(ChatColor.GREEN + "● Online");
            
            MapInstance map = api.getPlayerMapInstance((Player) friend);
            if (map != null) {
                lore.add(ChatColor.GRAY + "Playing: " + map.getMapName());
            }
        } else {
            lore.add(ChatColor.GRAY + "● Offline");
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        inv.setItem(slot++, item);
    }
    
    player.openInventory(inv);
}
```

---

## Schematic API

### Get Schematics

```java
// Get schematic for a map
String schematicName = api.getSchematicForMap("skyblock");

// Get all map-schematic mappings
Map<String, String> mappings = api.getAllMapSchematics();

for (Map.Entry<String, String> entry : mappings.entrySet()) {
    System.out.println("Map: " + entry.getKey());
    System.out.println("Schematic: " + entry.getValue());
}
```

---

## Config API

### Get Configuration Values

```java
// Get max players
int maxPlayers = api.getMaxPlayers();

// Get cleanup delay
long delay = api.getCleanupDelay();

// Check if map is globally blocked
boolean blocked = api.isMapGloballyBlocked("pvp_arena");
```

---

## Events

IcedSpear fires custom events that addons can listen to.

### Available Events

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

### Example Event Listener

```java
import com.stufy.fragmc.icedspear.api.events.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MapListener implements Listener {
    
    @EventHandler
    public void onPlayerJoinMap(PlayerJoinMapEvent event) {
        Player player = event.getPlayer();
        String mapName = event.getMapName();
        String instanceId = event.getInstanceId();
        
        player.sendMessage("Welcome to " + mapName + "!");
        
        // You can cancel the event
        // event.setCancelled(true);
    }
    
    @EventHandler
    public void onPlayerLeaveMap(PlayerLeaveMapEvent event) {
        Player player = event.getPlayer();
        String mapName = event.getMapName();
        
        // Track statistics, save data, etc.
    }
    
    @EventHandler
    public void onMapCreate(MapInstanceCreateEvent event) {
        String instanceId = event.getInstanceId();
        String mapName = event.getMapName();
        boolean isPublic = event.isPublic();
        
        System.out.println("New map created: " + mapName);
    }
    
    @EventHandler
    public void onPartyCreate(PartyCreateEvent event) {
        String code = event.getPartyCode();
        Player leader = event.getLeader();
        
        leader.sendMessage("Party system initialized!");
    }
}
```

### Register Event Listener

```java
@Override
public void onEnable() {
    getServer().getPluginManager().registerEvents(new MapListener(), this);
}
```

---

## Example Addon

### Complete Example: Map Statistics Tracker

```java
package com.example.mapstats;

import com.stufy.fragmc.icedspear.api.IcedSpearAPI;
import com.stufy.fragmc.icedspear.api.events.*;
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
    private IcedSpearAPI api;
    private Map<UUID, Long> joinTimes = new HashMap<>();
    private Map<UUID, Integer> playCount = new HashMap<>();
    
    @Override
    public void onEnable() {
        if (!setupAPI()) {
            getLogger().severe("IcedSpear not found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("mapstats").setExecutor(new StatsCommand(this));
        
        getLogger().info("MapStats addon enabled!");
    }
    
    private boolean setupAPI() {
        RegisteredServiceProvider<IcedSpearAPI> provider = 
            Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class);
        
        if (provider != null) {
            api = provider.getProvider();
            return true;
        }
        return false;
    }
    
    @EventHandler
    public void onPlayerJoinMap(PlayerJoinMapEvent event) {
        joinTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
}
```
