package com.stufy.fragmc.icedscore.config;

import java.awt.*;

/**
 * Fully parsed, immutable configuration for a single leaderboard display.
 */
public class LeaderboardConfig {

    // ── Core ────────────────────────────────────────────────────────────────
    public final String mapName;
    public final int entries;
    public final String imageFrameMap;
    public final int mapWidth;
    public final int mapHeight;

    // ── Title ───────────────────────────────────────────────────────────────
    public final String titleText;
    public final String titleFont;
    public final int titleFontSize;
    public final boolean titleBold;
    public final boolean titleItalic;
    public final Color titleColor;
    public final boolean titleShadow;
    public final Color titleShadowColor;
    public final Align titleAlign;

    // ── Background ──────────────────────────────────────────────────────────
    public final Color bgColor;
    public final int bgOpacity;
    public final boolean gradientEnabled;
    public final Color gradientTop;
    public final Color gradientBottom;
    public final boolean borderEnabled;
    public final Color borderColor;
    public final int borderThickness;
    public final int borderCornerRadius;

    // ── Columns ─────────────────────────────────────────────────────────────
    public final ColumnConfig rankCol;
    public final ColumnConfig playerCol;
    public final ColumnConfig timeCol;

    // ── Rows ────────────────────────────────────────────────────────────────
    public final int rowPadding;
    public final boolean alternatingEnabled;
    public final Color alternatingEven;
    public final Color alternatingOdd;
    public final boolean podiumEnabled;
    public final Color podium1;
    public final Color podium2;
    public final Color podium3;
    public final boolean rowSeparatorEnabled;
    public final Color rowSeparatorColor;
    public final int rowSeparatorThickness;

    // ── Header Row ──────────────────────────────────────────────────────────
    public final boolean headerRowEnabled;
    public final Color headerRowBg;
    public final Color headerRowColor;
    public final int headerRowFontSize;
    public final boolean headerRowBold;
    public final boolean headerSeparatorEnabled;
    public final Color headerSeparatorColor;
    public final int headerSeparatorThickness;

    // ── Footer ──────────────────────────────────────────────────────────────
    public final boolean footerEnabled;
    public final String footerText;
    public final Color footerColor;
    public final int footerFontSize;
    public final boolean footerItalic;
    public final Align footerAlign;

    // ── Inner padding ───────────────────────────────────────────────────────
    public final int padTop;
    public final int padBottom;
    public final int padLeft;
    public final int padRight;

    // ── Fonts ───────────────────────────────────────────────────────────────
    public final String defaultFont;

    public LeaderboardConfig(Builder b) {
        this.mapName = b.mapName;
        this.entries = b.entries;
        this.imageFrameMap = b.imageFrameMap;
        this.mapWidth = b.mapWidth;
        this.mapHeight = b.mapHeight;

        this.titleText = b.titleText;
        this.titleFont = b.titleFont;
        this.titleFontSize = b.titleFontSize;
        this.titleBold = b.titleBold;
        this.titleItalic = b.titleItalic;
        this.titleColor = b.titleColor;
        this.titleShadow = b.titleShadow;
        this.titleShadowColor = b.titleShadowColor;
        this.titleAlign = b.titleAlign;

        this.bgColor = b.bgColor;
        this.bgOpacity = b.bgOpacity;
        this.gradientEnabled = b.gradientEnabled;
        this.gradientTop = b.gradientTop;
        this.gradientBottom = b.gradientBottom;
        this.borderEnabled = b.borderEnabled;
        this.borderColor = b.borderColor;
        this.borderThickness = b.borderThickness;
        this.borderCornerRadius = b.borderCornerRadius;

        this.rankCol = b.rankCol;
        this.playerCol = b.playerCol;
        this.timeCol = b.timeCol;

        this.rowPadding = b.rowPadding;
        this.alternatingEnabled = b.alternatingEnabled;
        this.alternatingEven = b.alternatingEven;
        this.alternatingOdd = b.alternatingOdd;
        this.podiumEnabled = b.podiumEnabled;
        this.podium1 = b.podium1;
        this.podium2 = b.podium2;
        this.podium3 = b.podium3;
        this.rowSeparatorEnabled = b.rowSeparatorEnabled;
        this.rowSeparatorColor = b.rowSeparatorColor;
        this.rowSeparatorThickness = b.rowSeparatorThickness;

        this.headerRowEnabled = b.headerRowEnabled;
        this.headerRowBg = b.headerRowBg;
        this.headerRowColor = b.headerRowColor;
        this.headerRowFontSize = b.headerRowFontSize;
        this.headerRowBold = b.headerRowBold;
        this.headerSeparatorEnabled = b.headerSeparatorEnabled;
        this.headerSeparatorColor = b.headerSeparatorColor;
        this.headerSeparatorThickness = b.headerSeparatorThickness;

        this.footerEnabled = b.footerEnabled;
        this.footerText = b.footerText;
        this.footerColor = b.footerColor;
        this.footerFontSize = b.footerFontSize;
        this.footerItalic = b.footerItalic;
        this.footerAlign = b.footerAlign;

        this.padTop = b.padTop;
        this.padBottom = b.padBottom;
        this.padLeft = b.padLeft;
        this.padRight = b.padRight;

        this.defaultFont = b.defaultFont;
    }

    public int pixelWidth()  { return mapWidth  * 128; }
    public int pixelHeight() { return mapHeight * 128; }

    // ── Inner types ─────────────────────────────────────────────────────────

    public enum Align { LEFT, CENTER, RIGHT }

    public static class ColumnConfig {
        public final boolean enabled;
        public final String header;
        public final int widthPercent;
        public final Color color;
        public final String font;
        public final int fontSize;
        public final boolean bold;

        public ColumnConfig(boolean enabled, String header, int widthPercent,
                            Color color, String font, int fontSize, boolean bold) {
            this.enabled = enabled;
            this.header = header;
            this.widthPercent = widthPercent;
            this.color = color;
            this.font = font;
            this.fontSize = fontSize;
            this.bold = bold;
        }
    }

    // ── Builder ─────────────────────────────────────────────────────────────

    public static class Builder {
        public String mapName = "Unknown";
        public int entries = 10;
        public String imageFrameMap = "";
        public int mapWidth = 1;
        public int mapHeight = 2;

        public String titleText = "Leaderboard";
        public String titleFont = "Minecraft";
        public int titleFontSize = 16;
        public boolean titleBold = true;
        public boolean titleItalic = false;
        public Color titleColor = Color.YELLOW;
        public boolean titleShadow = true;
        public Color titleShadowColor = new Color(100, 80, 0);
        public Align titleAlign = Align.CENTER;

        public Color bgColor = new Color(26, 26, 46);
        public int bgOpacity = 220;
        public boolean gradientEnabled = true;
        public Color gradientTop = new Color(22, 33, 62);
        public Color gradientBottom = new Color(15, 52, 96);
        public boolean borderEnabled = true;
        public Color borderColor = new Color(79, 195, 247);
        public int borderThickness = 3;
        public int borderCornerRadius = 8;

        public ColumnConfig rankCol   = new ColumnConfig(true, "#",      10, new Color(144, 202, 249), "Minecraft", 11, false);
        public ColumnConfig playerCol = new ColumnConfig(true, "Player", 50, Color.WHITE,              "Minecraft", 11, false);
        public ColumnConfig timeCol   = new ColumnConfig(true, "Time",   40, new Color(165, 214, 167), "Minecraft", 11, false);

        public int rowPadding = 4;
        public boolean alternatingEnabled = true;
        public Color alternatingEven = new Color(0, 0, 0, 0);
        public Color alternatingOdd  = new Color(255, 255, 255, 15);
        public boolean podiumEnabled = true;
        public Color podium1 = new Color(255, 215, 0);
        public Color podium2 = new Color(192, 192, 192);
        public Color podium3 = new Color(205, 127, 50);
        public boolean rowSeparatorEnabled = false;
        public Color rowSeparatorColor = new Color(51, 51, 85);
        public int rowSeparatorThickness = 1;

        public boolean headerRowEnabled = true;
        public Color headerRowBg = new Color(13, 27, 42);
        public Color headerRowColor = new Color(100, 181, 246);
        public int headerRowFontSize = 10;
        public boolean headerRowBold = true;
        public boolean headerSeparatorEnabled = true;
        public Color headerSeparatorColor = new Color(79, 195, 247);
        public int headerSeparatorThickness = 2;

        public boolean footerEnabled = true;
        public String footerText = "Updated: {time}";
        public Color footerColor = new Color(84, 110, 122);
        public int footerFontSize = 8;
        public boolean footerItalic = true;
        public Align footerAlign = Align.RIGHT;

        public int padTop = 8;
        public int padBottom = 8;
        public int padLeft = 10;
        public int padRight = 10;

        public String defaultFont = "Minecraft";

        public LeaderboardConfig build() { return new LeaderboardConfig(this); }
    }
}