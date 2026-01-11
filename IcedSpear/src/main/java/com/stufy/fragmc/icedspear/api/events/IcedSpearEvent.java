package com.stufy.fragmc.icedspear.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public abstract class IcedSpearEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}