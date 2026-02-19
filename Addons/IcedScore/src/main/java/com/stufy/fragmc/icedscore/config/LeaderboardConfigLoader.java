package com.stufy.fragmc.icedscore.config;

import com.stufy.fragmc.icedscore.IcedScores;
import org.bukkit.configuration.ConfigurationSection;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class LeaderboardConfigLoader {

    private final IcedScores plugin;

    public LeaderboardConfigLoader(IcedScores plugin) {
        this.plugin = plugin;
    }

    public Map<String, LeaderboardConfig> loadAll() {
        Map<String, LeaderboardConfig> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("leaderboards");
        if (section == null) return result;

        for (String id : section.getKeys(false)) {
            ConfigurationSection lb = section.getConfigurationSection(id);
            if (lb == null) continue;
            try {
                result.put(id, load(lb));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load leaderboard '" + id + "': " + e.getMessage(), e);
            }
        }
        return result;
    }

    private LeaderboardConfig load(ConfigurationSection s) {
        LeaderboardConfig.Builder b = new LeaderboardConfig.Builder();

        b.mapName       = s.getString("map-name", b.mapName);
        b.entries       = s.getInt("entries", b.entries);
        b.imageFrameMap = s.getString("imageframe-map", b.imageFrameMap);
        b.mapWidth      = s.getInt("map-width", b.mapWidth);
        b.mapHeight     = s.getInt("map-height", b.mapHeight);

        // ── Title ────────────────────────────────────────────────────────────
        ConfigurationSection title = s.getConfigurationSection("title");
        if (title != null) {
            b.titleText        = title.getString("text", b.titleText);
            b.titleFont        = title.getString("font", b.titleFont);
            b.titleFontSize    = title.getInt("font-size", b.titleFontSize);
            b.titleBold        = title.getBoolean("bold", b.titleBold);
            b.titleItalic      = title.getBoolean("italic", b.titleItalic);
            b.titleColor       = parseColor(title.getString("color"), b.titleColor);
            b.titleShadow      = title.getBoolean("shadow", b.titleShadow);
            b.titleShadowColor = parseColor(title.getString("shadow-color"), b.titleShadowColor);
            b.titleAlign       = parseAlign(title.getString("align"), b.titleAlign);
        }

        // ── Background ───────────────────────────────────────────────────────
        ConfigurationSection bg = s.getConfigurationSection("background");
        if (bg != null) {
            b.bgColor   = parseColor(bg.getString("color"), b.bgColor);
            b.bgOpacity = bg.getInt("opacity", b.bgOpacity);
            ConfigurationSection grad = bg.getConfigurationSection("gradient");
            if (grad != null) {
                b.gradientEnabled = grad.getBoolean("enabled", b.gradientEnabled);
                b.gradientTop     = parseColor(grad.getString("top-color"), b.gradientTop);
                b.gradientBottom  = parseColor(grad.getString("bottom-color"), b.gradientBottom);
            }
            ConfigurationSection border = bg.getConfigurationSection("border");
            if (border != null) {
                b.borderEnabled      = border.getBoolean("enabled", b.borderEnabled);
                b.borderColor        = parseColor(border.getString("color"), b.borderColor);
                b.borderThickness    = border.getInt("thickness", b.borderThickness);
                b.borderCornerRadius = border.getInt("corner-radius", b.borderCornerRadius);
            }
        }

        // ── Columns ──────────────────────────────────────────────────────────
        ConfigurationSection cols = s.getConfigurationSection("columns");
        if (cols != null) {
            b.rankCol   = loadColumn(cols.getConfigurationSection("rank"),   b.rankCol);
            b.playerCol = loadColumn(cols.getConfigurationSection("player"), b.playerCol);
            b.timeCol   = loadColumn(cols.getConfigurationSection("time"),   b.timeCol);
        }

        // ── Rows ─────────────────────────────────────────────────────────────
        ConfigurationSection rows = s.getConfigurationSection("rows");
        if (rows != null) {
            b.rowPadding = rows.getInt("padding", b.rowPadding);
            ConfigurationSection alt = rows.getConfigurationSection("alternating");
            if (alt != null) {
                b.alternatingEnabled = alt.getBoolean("enabled", b.alternatingEnabled);
                b.alternatingEven    = parseColorARGB(alt.getString("even-color"), b.alternatingEven);
                b.alternatingOdd     = parseColorARGB(alt.getString("odd-color"), b.alternatingOdd);
            }
            ConfigurationSection podium = rows.getConfigurationSection("podium");
            if (podium != null) {
                b.podiumEnabled = podium.getBoolean("enabled", b.podiumEnabled);
                ConfigurationSection pColors = podium.getConfigurationSection("colors");
                if (pColors != null) {
                    b.podium1 = parseColor(pColors.getString("1"), b.podium1);
                    b.podium2 = parseColor(pColors.getString("2"), b.podium2);
                    b.podium3 = parseColor(pColors.getString("3"), b.podium3);
                }
            }
            ConfigurationSection sep = rows.getConfigurationSection("separator");
            if (sep != null) {
                b.rowSeparatorEnabled   = sep.getBoolean("enabled", b.rowSeparatorEnabled);
                b.rowSeparatorColor     = parseColor(sep.getString("color"), b.rowSeparatorColor);
                b.rowSeparatorThickness = sep.getInt("thickness", b.rowSeparatorThickness);
            }
        }

        // ── Header Row ───────────────────────────────────────────────────────
        ConfigurationSection header = s.getConfigurationSection("header-row");
        if (header != null) {
            b.headerRowEnabled  = header.getBoolean("enabled", b.headerRowEnabled);
            b.headerRowBg       = parseColor(header.getString("background-color"), b.headerRowBg);
            b.headerRowColor    = parseColor(header.getString("color"), b.headerRowColor);
            b.headerRowFontSize = header.getInt("font-size", b.headerRowFontSize);
            b.headerRowBold     = header.getBoolean("bold", b.headerRowBold);
            ConfigurationSection hsep = header.getConfigurationSection("separator");
            if (hsep != null) {
                b.headerSeparatorEnabled   = hsep.getBoolean("enabled", b.headerSeparatorEnabled);
                b.headerSeparatorColor     = parseColor(hsep.getString("color"), b.headerSeparatorColor);
                b.headerSeparatorThickness = hsep.getInt("thickness", b.headerSeparatorThickness);
            }
        }

        // ── Footer ───────────────────────────────────────────────────────────
        ConfigurationSection footer = s.getConfigurationSection("footer");
        if (footer != null) {
            b.footerEnabled  = footer.getBoolean("enabled", b.footerEnabled);
            b.footerText     = footer.getString("text", b.footerText);
            b.footerColor    = parseColor(footer.getString("color"), b.footerColor);
            b.footerFontSize = footer.getInt("font-size", b.footerFontSize);
            b.footerItalic   = footer.getBoolean("italic", b.footerItalic);
            b.footerAlign    = parseAlign(footer.getString("align"), b.footerAlign);
        }

        // ── Padding ──────────────────────────────────────────────────────────
        ConfigurationSection pad = s.getConfigurationSection("padding");
        if (pad != null) {
            b.padTop    = pad.getInt("top",    b.padTop);
            b.padBottom = pad.getInt("bottom", b.padBottom);
            b.padLeft   = pad.getInt("left",   b.padLeft);
            b.padRight  = pad.getInt("right",  b.padRight);
        }

        return b.build();
    }

    private LeaderboardConfig.ColumnConfig loadColumn(ConfigurationSection s,
                                                      LeaderboardConfig.ColumnConfig def) {
        if (s == null) return def;
        return new LeaderboardConfig.ColumnConfig(
                s.getBoolean("enabled", def.enabled),
                s.getString("header", def.header),
                s.getInt("width-percent", def.widthPercent),
                parseColor(s.getString("color"), def.color),
                s.getString("font", def.font),
                s.getInt("font-size", def.fontSize),
                s.getBoolean("bold", def.bold)
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Parses #RRGGBB hex strings. Alpha forced to 255. */
    public static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            hex = hex.trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) {
                int r = Integer.parseInt(hex.substring(0, 2), 16);
                int g = Integer.parseInt(hex.substring(2, 4), 16);
                int b = Integer.parseInt(hex.substring(4, 6), 16);
                return new Color(r, g, b, 255);
            }
        } catch (NumberFormatException ignored) {}
        return fallback;
    }

    /** Parses both #RRGGBB and #AARRGGBB (ARGB order when 8 digits). */
    public static Color parseColorARGB(String hex, Color fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            hex = hex.trim();
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 8) {
                int a = Integer.parseInt(hex.substring(0, 2), 16);
                int r = Integer.parseInt(hex.substring(2, 4), 16);
                int g = Integer.parseInt(hex.substring(4, 6), 16);
                int b = Integer.parseInt(hex.substring(6, 8), 16);
                return new Color(r, g, b, a);
            }
            return parseColor("#" + hex, fallback);
        } catch (NumberFormatException ignored) {}
        return fallback;
    }

    private static LeaderboardConfig.Align parseAlign(String s, LeaderboardConfig.Align fallback) {
        if (s == null) return fallback;
        try { return LeaderboardConfig.Align.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return fallback; }
    }
}