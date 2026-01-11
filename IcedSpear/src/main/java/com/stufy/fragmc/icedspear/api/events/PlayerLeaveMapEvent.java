package com.stufy.fragmc.icedspear.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerLeaveMapEvent extends IcedSpearEvent {
    private static final HandlerList handlers = new HandlerList();
    private final Player player;
    private final String instanceId;
    private final String mapName;

    public PlayerLeaveMapEvent(Player player, String instanceId, String mapName) {
        this.player = player;
        this.instanceId = instanceId;
        this.mapName = mapName;
    }

    public Player getPlayer() {
        return player;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getMapName() {
        return mapName;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}