package com.fragmc.weblink;

import com.stufy.fragmc.icedspear.api.IcedSpearAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class WebLinkAddon extends JavaPlugin {

    private static WebLinkAddon instance;

    private IcedSpearAPI    icedSpearAPI;
    private Database        database;
    private LinkManager     linkManager;
    private SecurityManager securityManager;
    private WebhookServer   webhookServer;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 1. IcedSpear
        if (!setupIcedSpear()) {
            getLogger().severe("IcedSpear not found! Disabling WebLink.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. SQLite — creates the DB file inside the plugin data folder if it doesn't exist
        database = new Database(this);

        // 3. Security (reads webhook-secret from config)
        securityManager = new SecurityManager(this);

        // 4. LinkManager — loads existing rows from SQLite into memory
        linkManager = new LinkManager(this, database);

        // 5. Commands
        getCommand("weblink").setExecutor(new WebLinkCommand(this));

        // 6. Webhook HTTP server
        webhookServer = new WebhookServer(this);
        webhookServer.start();

        getLogger().info("WebLink Addon enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (webhookServer != null) webhookServer.stop();
        if (linkManager   != null) linkManager.cleanup();
        if (database       != null) database.close();
        getLogger().info("WebLink Addon disabled.");
    }

    // ------------------------------------------------------------------

    private boolean setupIcedSpear() {
        RegisteredServiceProvider<IcedSpearAPI> provider =
                Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class);
        if (provider != null) {
            icedSpearAPI = provider.getProvider();
            return true;
        }
        return false;
    }

    // Accessors
    public static WebLinkAddon getInstance()     { return instance; }
    public IcedSpearAPI    getIcedSpearAPI()     { return icedSpearAPI; }
    public Database        getDatabase()         { return database; }
    public LinkManager     getLinkManager()      { return linkManager; }
    public SecurityManager getSecurityManager()  { return securityManager; }
}