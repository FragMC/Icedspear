package com.stufy.fragmc.icedspear.api.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Called when a player joins a map instance
 */
public class PlayerJoinMapEvent extends IcedSpearEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private final Player player;
    private final String instanceId;
    private final String mapName;

    public PlayerJoinMapEvent(Player player, String instanceId, String mapName) {
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
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}