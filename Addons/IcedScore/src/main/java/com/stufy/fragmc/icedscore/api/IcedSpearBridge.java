package com.stufy.fragmc.icedscore.api;

import com.stufy.fragmc.icedspear.api.IcedSpearAPI;
import com.stufy.fragmc.icedspear.models.LeaderboardEntry;
import com.stufy.fragmc.icedscore.IcedScores;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

/**
 * Thin bridge between IcedScores and IcedSpear's leaderboard API.
 *
 * IcedSpear package: com.stufy.fragmc.icedspear
 * API class:         com.stufy.fragmc.icedspear.api.IcedSpearAPI
 * Entry class:       com.stufy.fragmc.icedspear.models.LeaderboardEntry
 *
 * The API is resolved once via Bukkit's ServicesManager and cached.
 */
public class IcedSpearBridge {

    /**
     * Thin data record wrapping LeaderboardEntry fields.
     * Decouples the rest of IcedScores from IcedSpear's classes at the cost
     * of a tiny allocation per render cycle.
     */
    public record Entry(String playerName, String formattedTime, long rawTimeMs) {}

    private static IcedSpearAPI cachedApi = null;

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the top {@code limit} entries for the given IcedSpear map,
     * sorted best time first.  Returns an empty list on any error.
     */
    public static List<Entry> getTopTimes(String mapName, int limit) {
        IcedSpearAPI api = getApi();
        if (api == null) return Collections.emptyList();

        try {
            List<LeaderboardEntry> raw = api.getTopTimes(mapName, limit);
            if (raw == null || raw.isEmpty()) return Collections.emptyList();

            List<Entry> result = new ArrayList<>(raw.size());
            for (LeaderboardEntry e : raw) {
                result.add(new Entry(
                        e.getPlayerName(),
                        e.getFormattedTime(),
                        e.getTime()
                ));
            }
            return result;

        } catch (Exception e) {
            IcedScores.getInstance().getLogger().log(Level.WARNING,
                    "[IcedScores] Failed to fetch IcedSpear leaderboard for map '" + mapName + "'", e);
            return Collections.emptyList();
        }
    }

    // ─── Internal API resolution ─────────────────────────────────────────────

    private static IcedSpearAPI getApi() {
        if (cachedApi != null) return cachedApi;

        var reg = Bukkit.getServicesManager().getRegistration(IcedSpearAPI.class);
        if (reg == null) {
            IcedScores.getInstance().getLogger().severe(
                    "[IcedScores] IcedSpear is not registered in the ServicesManager. "
                            + "Is the IcedSpear plugin loaded and enabled?");
            return null;
        }

        cachedApi = reg.getProvider();
        IcedScores.getInstance().getLogger().info("[IcedScores] IcedSpear API resolved successfully.");
        return cachedApi;
    }

    /** Call this on plugin reload so the API reference is re-resolved. */
    public static void reset() {
        cachedApi = null;
    }
}