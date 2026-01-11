package com.stufy.fragmc.icedspear;

import com.stufy.fragmc.icedspear.api.IcedSpearAPI;
import com.stufy.fragmc.icedspear.commands.*;
import com.stufy.fragmc.icedspear.managers.*;
import com.stufy.fragmc.icedspear.listeners.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class IcedSpear extends JavaPlugin {
    private MapManager mapManager;
    private PartyManager partyManager;
    private FriendManager friendManager;
    private ConfigManager configManager;
    private SchematicManager schematicManager;
    private LeaderboardManager leaderboardManager;
    private TimerManager timerManager;
    private IcedSpearAPI api;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        schematicManager = new SchematicManager(this);
        leaderboardManager = new LeaderboardManager(this);
        mapManager = new MapManager(this, schematicManager, configManager);
        partyManager = new PartyManager(this, mapManager, configManager);
        friendManager = new FriendManager(this);
        timerManager = new TimerManager(this, leaderboardManager, partyManager, mapManager);

        // Initialize API
        api = new IcedSpearAPI(this, mapManager, partyManager, friendManager, schematicManager, configManager);

        // Register API as a service for other plugins
        getServer().getServicesManager().register(IcedSpearAPI.class, api, this, ServicePriority.Normal);

        // Register commands
        getCommand("map").setExecutor(new MapCommand(mapManager, partyManager));
        getCommand("party").setExecutor(new PartyCommand(partyManager, friendManager));
        getCommand("friend").setExecutor(new FriendCommand(friendManager, mapManager));
        getCommand("icedspear").setExecutor(new IcedSpearCommand(this, mapManager, configManager, schematicManager));
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(leaderboardManager, mapManager, schematicManager));

        // Register listeners
        getServer().getPluginManager().registerEvents(new MapListener(mapManager, configManager, timerManager), this);
        getServer().getPluginManager().registerEvents(new PartyListener(partyManager), this);

        getLogger().info("IcedSpear has been enabled!");
        getLogger().info("API registered and available for addons!");
    }

    @Override
    public void onDisable() {
        // Stop timer manager
        if (timerManager != null) {
            timerManager.shutdown();
        }

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

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }

    public IcedSpearAPI getAPI() {
        return api;
    }
}