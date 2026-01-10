package com.stufy.fragmc.icedspear.models;

import org.bukkit.Location;
import org.bukkit.World;
import java.util.*;

public class MapInstance {
    private final String instanceId;
    private final String mapName;
    private final boolean isPublic;
    private World world;
    private Location spawnLocation;
    private MapState state;
    private final Set<UUID> players;
    private final Set<UUID> waitingPlayers;
    private final long createdAt;

    public MapInstance(String instanceId, String mapName, boolean isPublic) {
        this.instanceId = instanceId;
        this.mapName = mapName;
        this.isPublic = isPublic;
        this.state = MapState.CREATING;
        this.players = new HashSet<>();
        this.waitingPlayers = new HashSet<>();
        this.createdAt = System.currentTimeMillis();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getMapName() {
        return mapName;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public World getWorld() {
        return world;
    }

    public void setWorld(World world) {
        this.world = world;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public MapState getState() {
        return state;
    }

    public void setState(MapState state) {
        this.state = state;
    }

    public Set<UUID> getPlayers() {
        return new HashSet<>(players);
    }

    public void addPlayer(UUID playerId) {
        players.add(playerId);
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
        waitingPlayers.remove(playerId);
    }

    public void addWaitingPlayer(UUID playerId) {
        waitingPlayers.add(playerId);
    }

    public Set<UUID> getWaitingPlayers() {
        return new HashSet<>(waitingPlayers);
    }

    public void clearWaitingPlayers() {
        waitingPlayers.clear();
    }

    public long getCreatedAt() {
        return createdAt;
    }
}