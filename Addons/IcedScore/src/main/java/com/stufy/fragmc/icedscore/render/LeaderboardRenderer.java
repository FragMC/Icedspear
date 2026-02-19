package com.stufy.fragmc.icedscore.render;

import com.loohp.imageframe.ImageFrame;
import com.loohp.imageframe.objectholders.DiagramImageMap;
import com.loohp.imageframe.objectholders.ImageMap;
import com.stufy.fragmc.icedscore.IcedScores;
import com.stufy.fragmc.icedscore.api.IcedSpearBridge;
import com.stufy.fragmc.icedscore.config.LeaderboardConfig;
import com.stufy.fragmc.icedscore.config.LeaderboardConfig.Align;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Renders leaderboard data into a BufferedImage and pushes it to an
 * ImageFrame DiagramImageMap located by name.
 *
 * HOW TO SET UP IN-GAME:
 *   /imageframe diagram <name> <width> <height>
 *   e.g. /imageframe diagram snowfall_board 1 2
 *
 * The <name> must match `imageframe-map` in config.yml.
 * DiagramImageMap is the ImageFrame type designed for programmatic image updates.
 */
public class LeaderboardRenderer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final IcedScores plugin;
    private final FontRegistry fonts;

    public LeaderboardRenderer(IcedScores plugin, FontRegistry fonts) {
        this.plugin = plugin;
        this.fonts  = fonts;
    }

    // ─── Public entry point ──────────────────────────────────────────────────

    public void render(String id, LeaderboardConfig cfg) {
        List<IcedSpearBridge.Entry> entries = IcedSpearBridge.getTopTimes(cfg.mapName, cfg.entries);
        BufferedImage image = buildImage(cfg, entries);
        pushToImageFrame(id, cfg, image);
    }

    // ─── Image construction ──────────────────────────────────────────────────

    private BufferedImage buildImage(LeaderboardConfig cfg, List<IcedSpearBridge.Entry> entries) {
        int W = cfg.pixelWidth();
        int H = cfg.pixelHeight();

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        drawBackground(g, cfg, W, H);
        if (cfg.borderEnabled) drawBorder(g, cfg, W, H);

        int cx = cfg.padLeft;
        int cy = cfg.padTop;
        int cw = W - cfg.padLeft - cfg.padRight;
        int ch = H - cfg.padTop  - cfg.padBottom;

        int titleH = drawTitle(g, cfg, cx, cy, cw);
        cy += titleH + 4;

        if (cfg.headerRowEnabled) {
            int hh = drawHeaderRow(g, cfg, cx, cy, cw);
            cy += hh;
        }

        drawRows(g, cfg, entries, cx, cy, cw, ch - (cy - cfg.padTop));

        if (cfg.footerEnabled) drawFooter(g, cfg, W, H);

        g.dispose();
        return img;
    }

    // ─── Section drawing ─────────────────────────────────────────────────────

    private void drawBackground(Graphics2D g, LeaderboardConfig cfg, int W, int H) {
        if (cfg.gradientEnabled) {
            GradientPaint gp = new GradientPaint(
                    0, 0, applyAlpha(cfg.gradientTop,    cfg.bgOpacity),
                    0, H, applyAlpha(cfg.gradientBottom, cfg.bgOpacity));
            g.setPaint(gp);
        } else {
            g.setColor(applyAlpha(cfg.bgColor, cfg.bgOpacity));
        }
        if (cfg.borderEnabled && cfg.borderCornerRadius > 0) {
            g.fill(new RoundRectangle2D.Float(0, 0, W, H,
                    cfg.borderCornerRadius * 2f, cfg.borderCornerRadius * 2f));
        } else {
            g.fillRect(0, 0, W, H);
        }
    }

    private void drawBorder(Graphics2D g, LeaderboardConfig cfg, int W, int H) {
        g.setColor(cfg.borderColor);
        g.setStroke(new BasicStroke(cfg.borderThickness));
        float half = cfg.borderThickness / 2f;
        if (cfg.borderCornerRadius > 0) {
            float r = cfg.borderCornerRadius * 2f;
            g.draw(new RoundRectangle2D.Float(half, half,
                    W - cfg.borderThickness, H - cfg.borderThickness, r, r));
        } else {
            g.drawRect((int) half, (int) half, W - cfg.borderThickness, H - cfg.borderThickness);
        }
        g.setStroke(new BasicStroke(1));
    }

    private int drawTitle(Graphics2D g, LeaderboardConfig cfg, int x, int y, int w) {
        Font f = fonts.get(cfg.titleFont, cfg.titleFontSize, cfg.titleBold, cfg.titleItalic);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        String text = cfg.titleText;
        int tx = alignedX(g, text, x, w, cfg.titleAlign);
        int ty = y + fm.getAscent();
        if (cfg.titleShadow) {
            g.setColor(cfg.titleShadowColor);
            g.drawString(text, tx + 1, ty + 1);
        }
        g.setColor(cfg.titleColor);
        g.drawString(text, tx, ty);
        return fm.getHeight() + 2;
    }

    private int drawHeaderRow(Graphics2D g, LeaderboardConfig cfg, int x, int y, int w) {
        Font f = fonts.get(cfg.defaultFont, cfg.headerRowFontSize, cfg.headerRowBold, false);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int rowH = fm.getHeight() + cfg.rowPadding * 2;

        g.setColor(cfg.headerRowBg);
        g.fillRect(x, y, w, rowH);

        int[] colXs = computeColumnXs(cfg, x, w);
        g.setColor(cfg.headerRowColor);
        int ty = y + cfg.rowPadding + fm.getAscent();

        if (cfg.rankCol.enabled)   g.drawString(cfg.rankCol.header,   colXs[0], ty);
        if (cfg.playerCol.enabled) g.drawString(cfg.playerCol.header, colXs[1], ty);
        if (cfg.timeCol.enabled)   drawRightAligned(g, cfg.timeCol.header, colXs[2], x + w, ty);

        int consumed = rowH;
        if (cfg.headerSeparatorEnabled) {
            g.setColor(cfg.headerSeparatorColor);
            g.setStroke(new BasicStroke(cfg.headerSeparatorThickness));
            g.drawLine(x, y + rowH, x + w, y + rowH);
            g.setStroke(new BasicStroke(1));
            consumed += cfg.headerSeparatorThickness;
        }
        return consumed;
    }

    private void drawRows(Graphics2D g, LeaderboardConfig cfg,
                          List<IcedSpearBridge.Entry> entries,
                          int x, int y, int w, int availH) {
        if (entries.isEmpty()) {
            Font f = fonts.get(cfg.defaultFont, cfg.playerCol.fontSize, false, true);
            g.setFont(f);
            g.setColor(new Color(120, 120, 120));
            g.drawString("No times recorded.", x + 4, y + g.getFontMetrics().getAscent() + 4);
            return;
        }

        Font rankFont   = fonts.get(cfg.rankCol.font,   cfg.rankCol.fontSize,   cfg.rankCol.bold,   false);
        Font playerFont = fonts.get(cfg.playerCol.font, cfg.playerCol.fontSize, cfg.playerCol.bold, false);
        Font timeFont   = fonts.get(cfg.timeCol.font,   cfg.timeCol.fontSize,   cfg.timeCol.bold,   false);

        g.setFont(playerFont);
        FontMetrics fm = g.getFontMetrics();
        int rowH = fm.getHeight() + cfg.rowPadding * 2;
        int[] colXs = computeColumnXs(cfg, x, w);

        for (int i = 0; i < entries.size(); i++) {
            if ((y - cfg.padTop) + rowH > availH + cfg.padTop) break;

            IcedSpearBridge.Entry entry = entries.get(i);
            int rank = i + 1;

            if (cfg.alternatingEnabled) {
                Color rowBg = (i % 2 == 0) ? cfg.alternatingEven : cfg.alternatingOdd;
                if (rowBg.getAlpha() > 0) {
                    g.setColor(rowBg);
                    g.fillRect(x, y, w, rowH);
                }
            }

            int ty = y + cfg.rowPadding + fm.getAscent();

            Color textColor = cfg.playerCol.color;
            if (cfg.podiumEnabled) {
                textColor = switch (rank) {
                    case 1 -> cfg.podium1;
                    case 2 -> cfg.podium2;
                    case 3 -> cfg.podium3;
                    default -> cfg.playerCol.color;
                };
            }

            if (cfg.rankCol.enabled) {
                g.setFont(rankFont);
                g.setColor(cfg.podiumEnabled && rank <= 3 ? textColor : cfg.rankCol.color);
                g.drawString(rank + ".", colXs[0], ty);
            }

            if (cfg.playerCol.enabled) {
                g.setFont(playerFont);
                g.setColor(textColor);
                drawClipped(g, entry.playerName(), colXs[1], ty, colXs[2] - colXs[1] - 4);
            }

            if (cfg.timeCol.enabled) {
                g.setFont(timeFont);
                g.setColor(cfg.podiumEnabled && rank <= 3 ? textColor : cfg.timeCol.color);
                drawRightAligned(g, entry.formattedTime(), colXs[2], x + w, ty);
            }

            if (cfg.rowSeparatorEnabled) {
                g.setColor(cfg.rowSeparatorColor);
                g.setStroke(new BasicStroke(cfg.rowSeparatorThickness));
                g.drawLine(x, y + rowH, x + w, y + rowH);
                g.setStroke(new BasicStroke(1));
            }

            y += rowH;
        }
    }

    private void drawFooter(Graphics2D g, LeaderboardConfig cfg, int W, int H) {
        Font f = fonts.get(cfg.defaultFont, cfg.footerFontSize, false, cfg.footerItalic);
        g.setFont(f);
        String text = cfg.footerText.replace("{time}", LocalTime.now().format(TIME_FMT));
        g.setColor(cfg.footerColor);
        int x = alignedX(g, text, cfg.padLeft, W - cfg.padLeft - cfg.padRight, cfg.footerAlign);
        g.drawString(text, x, H - cfg.padBottom);
    }

    // ─── Column layout ───────────────────────────────────────────────────────

    private int[] computeColumnXs(LeaderboardConfig cfg, int x, int w) {
        int rankW   = cfg.rankCol.enabled   ? (w * cfg.rankCol.widthPercent   / 100) : 0;
        int playerW = cfg.playerCol.enabled ? (w * cfg.playerCol.widthPercent / 100) : 0;
        return new int[]{ x, x + rankW, x + rankW + playerW };
    }

    // ─── Drawing helpers ─────────────────────────────────────────────────────

    private int alignedX(Graphics2D g, String text, int x, int w, Align align) {
        return switch (align) {
            case CENTER -> x + (w - g.getFontMetrics().stringWidth(text)) / 2;
            case RIGHT  -> x + w - g.getFontMetrics().stringWidth(text);
            default     -> x;
        };
    }

    private void drawRightAligned(Graphics2D g, String text, int colStart, int colEnd, int y) {
        g.drawString(text, colEnd - g.getFontMetrics().stringWidth(text), y);
    }

    private void drawClipped(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxWidth) { g.drawString(text, x, y); return; }
        int ellipsisW = fm.stringWidth("…");
        while (text.length() > 1 && fm.stringWidth(text) + ellipsisW > maxWidth)
            text = text.substring(0, text.length() - 1);
        g.drawString(text + "…", x, y);
    }

    private Color applyAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.min(255, alpha));
    }

    // ─── ImageFrame push ─────────────────────────────────────────────────────

    /**
     * Locates the DiagramImageMap by name and calls setAndSendImage().
     *
     * Create the map in-game first:
     *   /imageframe diagram <imageframe-map> <map-width> <map-height>
     */
    private void pushToImageFrame(String leaderboardId, LeaderboardConfig cfg, BufferedImage image) {
        try {
            // Search all maps by name (case-insensitive)
            Optional<ImageMap> found = ImageFrame.imageMapManager.getImageMaps().stream()
                    .filter(m -> cfg.imageFrameMap.equalsIgnoreCase(m.getName()))
                    .findFirst();

            if (found.isEmpty()) {
                plugin.getLogger().warning(
                        "[IcedScores] ImageFrame DiagramImageMap '" + cfg.imageFrameMap
                                + "' not found for leaderboard '" + leaderboardId + "'. "
                                + "Create it in-game: /imageframe diagram "
                                + cfg.imageFrameMap + " " + cfg.mapWidth + " " + cfg.mapHeight);
                return;
            }

            ImageMap map = found.get();

            if (!(map instanceof DiagramImageMap diagram)) {
                plugin.getLogger().warning(
                        "[IcedScores] '" + cfg.imageFrameMap + "' is not a DiagramImageMap "
                                + "(type: " + map.getClass().getSimpleName() + "). "
                                + "Delete and recreate with: /imageframe diagram "
                                + cfg.imageFrameMap + " " + cfg.mapWidth + " " + cfg.mapHeight);
                return;
            }

            // Apply image and push map packets to all current viewers
            diagram.setAndSendImage(image, diagram.getViewers());

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[IcedScores] Error pushing to ImageFrame map '" + cfg.imageFrameMap + "'", e);
        }
    }
}