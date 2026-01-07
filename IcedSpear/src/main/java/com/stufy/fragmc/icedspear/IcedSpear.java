package com.stufy.fragmc.icedspear;

import com.stufy.fragmc.icedspear.commands.*;
import com.stufy.fragmc.icedspear.managers.*;
import com.stufy.fragmc.icedspear.listeners.*;
import org.bukkit.plugin.java.JavaPlugin;

public class IcedSpear extends JavaPlugin {
    private MapManager mapManager;
    private PartyManager partyManager;
    private FriendManager friendManager;
    private ConfigManager configManager;
    private SchematicManager schematicManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        schematicManager = new SchematicManager(this);
        mapManager = new MapManager(this, schematicManager, configManager);
        partyManager = new PartyManager(this, mapManager);
        friendManager = new FriendManager(this);

        // Register commands
        getCommand("map").setExecutor(new MapCommand(mapManager, partyManager));
        getCommand("party").setExecutor(new PartyCommand(partyManager));
        getCommand("friend").setExecutor(new FriendCommand(friendManager));
        getCommand("icedspear").setExecutor(new IcedSpearCommand(this, mapManager, configManager, schematicManager));

        // Register listeners
        getServer().getPluginManager().registerEvents(new MapListener(mapManager), this);
        getServer().getPluginManager().registerEvents(new PartyListener(partyManager), this);

        getLogger().info("IcedSpear has been enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up all active maps
        if (mapManager != null) {
            mapManager.cleanup();
        }
        getLogger().info("IcedSpear has been disabled!");
    }

    public MapManager getMapManager() {
        return mapManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

    public FriendManager getFriendManager() {
        return friendManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }
}