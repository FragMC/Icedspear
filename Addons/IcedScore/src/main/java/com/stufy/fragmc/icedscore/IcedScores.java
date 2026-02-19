package com.stufy.fragmc.icedscore;

import com.stufy.fragmc.icedscore.command.IcedScoreCommand;
import com.stufy.fragmc.icedscore.config.LeaderboardConfig;
import com.stufy.fragmc.icedscore.config.LeaderboardConfigLoader;
import com.stufy.fragmc.icedscore.render.LeaderboardRenderer;
import com.stufy.fragmc.icedscore.render.FontRegistry;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

public final class IcedScores extends JavaPlugin {

    private static IcedScores instance;

    private FontRegistry fontRegistry;
    private LeaderboardConfigLoader configLoader;
    private LeaderboardRenderer renderer;
    private BukkitTask refreshTask;

    private Map<String, LeaderboardConfig> leaderboards = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        instance = this;

        // Save default config & fonts folder
        saveDefaultConfig();
        File fontsDir = new File(getDataFolder(), "fonts");
        if (!fontsDir.exists()) fontsDir.mkdirs();
        // Save bundled Minecraft font
        saveResource("fonts/minecraft.ttf", false);

        fontRegistry = new FontRegistry(fontsDir);
        configLoader = new LeaderboardConfigLoader(this);
        renderer = new LeaderboardRenderer(this, fontRegistry);

        loadAll();

        // Register command
        Objects.requireNonNull(getCommand("icedscore"))
                .setExecutor(new IcedScoreCommand(this));

        getLogger().info("IcedScores enabled with " + leaderboards.size() + " leaderboard(s).");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        getLogger().info("IcedScores disabled.");
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    public void loadAll() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        reloadConfig();
        fontRegistry.reload();
        leaderboards = configLoader.loadAll();

        int intervalTicks = getConfig().getInt("refresh-interval", 100);
        refreshTask = Bukkit.getScheduler().runTaskTimer(this, this::refreshAll, 20L, intervalTicks);
    }

    public void refreshAll() {
        for (Map.Entry<String, LeaderboardConfig> entry : leaderboards.entrySet()) {
            try {
                renderer.render(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to render leaderboard '" + entry.getKey() + "'", e);
            }
        }
    }

    public void refreshSingle(String id) {
        LeaderboardConfig cfg = leaderboards.get(id);
        if (cfg == null) return;
        try {
            renderer.render(id, cfg);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to render leaderboard '" + id + "'", e);
        }
    }

    public Map<String, LeaderboardConfig> getLeaderboards() {
        return Collections.unmodifiableMap(leaderboards);
    }

    public FontRegistry getFontRegistry() { return fontRegistry; }
    public LeaderboardRenderer getRenderer() { return renderer; }

    public static IcedScores getInstance() { return instance; }
}