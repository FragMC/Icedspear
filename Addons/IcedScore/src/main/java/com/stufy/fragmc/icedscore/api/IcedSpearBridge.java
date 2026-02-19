package com.stufy.fragmc.icedscore.api;

import com.stufy.fragmc.icedscore.IcedScores;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Runtime bridge to the IcedSpear leaderboard API.
 *
 * We resolve everything via reflection so that:
 *  - The exact package path of IcedSpear doesn't need to be correct at compile time.
 *  - A clear, actionable error is logged if the API isn't found.
 *
 * For compile-time resolution (recommended once you confirm the package),
 * replace the reflection calls with direct imports of IcedSpearAPI and LeaderboardEntry.
 *
 * The expected IcedSpear service interface name is one of:
 *   dev.icedspear.api.IcedSpearAPI
 *   com.icedspear.api.IcedSpearAPI
 *   (check your IcedSpear jar's package with: jar tf IcedSpear.jar | grep API)
 */
public class IcedSpearBridge {

    /** Thin wrapper around a LeaderboardEntry so the rest of the plugin is decoupled from IcedSpear's classes. */
    public record Entry(String playerName, String formattedTime, long rawTimeMs) {}

    // ─── Cached reflection handles ────────────────────────────────────────────

    private static Object cachedApi      = null;
    private static Method cachedGetTop   = null;
    private static Class<?> entryClass   = null;
    private static Method entryGetName   = null;
    private static Method entryGetFmt    = null;
    private static Method entryGetTime   = null;
    private static boolean initFailed    = false;

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the top N entries for the given IcedSpear map name.
     * Returns an empty list if IcedSpear is unavailable or has no data.
     */
    public static List<Entry> getTopTimes(String mapName, int limit) {
        if (initFailed) return Collections.emptyList();

        try {
            ensureInit();
            if (cachedApi == null) return Collections.emptyList();

            // Call: List<LeaderboardEntry> getTopTimes(String, int)
            Object rawList = cachedGetTop.invoke(cachedApi, mapName, limit);
            if (!(rawList instanceof List<?> list)) return Collections.emptyList();

            List<Entry> results = new ArrayList<>(list.size());
            for (Object raw : list) {
                if (raw == null) continue;
                String name = (String) entryGetName.invoke(raw);
                String fmt  = (String) entryGetFmt.invoke(raw);
                long   ms   = (long)   entryGetTime.invoke(raw);
                results.add(new Entry(name, fmt, ms));
            }
            return results;

        } catch (Exception e) {
            IcedScores.getInstance().getLogger().log(Level.WARNING,
                    "[IcedScores] Failed to fetch IcedSpear leaderboard data for map '" + mapName + "'", e);
            return Collections.emptyList();
        }
    }

    // ─── Reflection init ─────────────────────────────────────────────────────

    /**
     * Tries candidate package paths to find the IcedSpear service.
     * Extend the CANDIDATES array if your IcedSpear version uses a different package.
     */
    private static void ensureInit() throws Exception {
        if (cachedApi != null || initFailed) return;

        // ── Candidate class names to try ──────────────────────────────────────
        // Run: jar tf plugins/IcedSpear-*.jar | grep -i "IcedSpearAPI"
        // to find the correct one and update this list.
        String[] candidates = {
                "dev.icedspear.api.IcedSpearAPI",
                "com.icedspear.api.IcedSpearAPI",
                "me.icedspear.api.IcedSpearAPI",
                "dev.loohp.icedspear.api.IcedSpearAPI",   // guessed from ImageFrame author pattern
                "net.icedspear.api.IcedSpearAPI",
        };

        Class<?> apiClass = null;
        for (String candidate : candidates) {
            try {
                apiClass = Class.forName(candidate);
                break;
            } catch (ClassNotFoundException ignored) {}
        }

        if (apiClass == null) {
            logApiNotFound(candidates);
            initFailed = true;
            return;
        }

        // Resolve from Bukkit ServicesManager
        var registration = Bukkit.getServicesManager().getRegistration(
                (Class<Object>) apiClass);

        if (registration == null) {
            IcedScores.getInstance().getLogger().severe(
                    "[IcedScores] IcedSpear class found (" + apiClass.getName()
                            + ") but it is NOT registered in the ServicesManager. "
                            + "Is IcedSpear actually loaded?");
            initFailed = true;
            return;
        }

        cachedApi = registration.getProvider();

        // Find getTopTimes(String, int) method
        cachedGetTop = apiClass.getMethod("getTopTimes", String.class, int.class);

        // Find LeaderboardEntry class (same package as API)
        String entryClassName = apiClass.getPackageName() + ".LeaderboardEntry";
        entryClass = Class.forName(entryClassName);

        entryGetName = entryClass.getMethod("getPlayerName");
        entryGetFmt  = entryClass.getMethod("getFormattedTime");
        entryGetTime = entryClass.getMethod("getTime");

        IcedScores.getInstance().getLogger().info(
                "[IcedScores] IcedSpear API resolved: " + apiClass.getName());
    }

    private static void logApiNotFound(String[] candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("[IcedScores] Could not find IcedSpear API class. Tried:\n");
        for (String c : candidates) sb.append("  - ").append(c).append("\n");
        sb.append("To fix: run `jar tf plugins/IcedSpear-*.jar | grep -i IcedSpearAPI` on your server,\n");
        sb.append("then add the correct class name to IcedSpearBridge.candidates[].");
        IcedScores.getInstance().getLogger().severe(sb.toString());
    }
}