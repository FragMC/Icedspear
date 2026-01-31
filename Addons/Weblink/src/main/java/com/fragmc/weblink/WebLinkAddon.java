package com.fragmc.weblink;

import com.stufy.fragmc.icedspear.api.IcedSpearAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class WebLinkAddon extends JavaPlugin {
    private static WebLinkAddon instance;
    private IcedSpearAPI icedSpearAPI;
    private WebhookServer webhookServer;
    private LinkManager linkManager;
    private SecurityManager securityManager;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Setup IcedSpear API
        if (!setupIcedSpear()) {
            getLogger().severe("IcedSpear not found! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        securityManager = new SecurityManager(this);
        linkManager = new LinkManager(this);

        // Register commands
        getCommand("weblink").setExecutor(new WebLinkCommand(this));

        // Start webhook server
        webhookServer = new WebhookServer(this);
        webhookServer.start();

        getLogger().info("WebLink Addon enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (webhookServer != null) {
            webhookServer.stop();
        }
        if (linkManager != null) {
            linkManager.cleanup();
        }
        getLogger().info("WebLink Addon disabled!");
    }

    private boolean setupIcedSpear() {
        RegisteredServiceProvider<IcedSpearAPI> provider =
                Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class);

        if (provider != null) {
            icedSpearAPI = provider.getProvider();
            return true;
        }
        return false;
    }

    public static WebLinkAddon getInstance() {
        return instance;
    }

    public IcedSpearAPI getIcedSpearAPI() {
        return icedSpearAPI;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public SecurityManager getSecurityManager() {
        return securityManager;
    }
}