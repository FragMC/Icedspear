package com.stufy.fragmc.icedspear.listeners;

import com.stufy.fragmc.icedspear.managers.PartyManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PartyListener implements Listener {
    private final PartyManager partyManager;

    public PartyListener(PartyManager partyManager) {
        this.partyManager = partyManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String partyCode = partyManager.getPlayerParty(player.getUniqueId());

        if (partyCode != null) {
            partyManager.leaveParty(player);
        }
    }
}