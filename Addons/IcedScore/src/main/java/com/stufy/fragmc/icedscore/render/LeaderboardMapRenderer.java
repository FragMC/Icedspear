package com.stufy.fragmc.icedscore.render;

import com.loohp.imageframe.objectholders.ImageMap;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * A Bukkit {@link MapRenderer} that paints a pre-rendered {@link BufferedImage}
 * slice onto a single 128×128 map tile.
 *
 * ImageFrame maps are backed by one or more {@link MapView}s (one per tile in
 * the grid).  We install one instance of this renderer on every tile's MapView,
 * giving each one the correct 128×128 crop of the full leaderboard image.
 */
public class LeaderboardMapRenderer extends MapRenderer {

    /** The 128×128 slice of the full image assigned to this tile. */
    private volatile BufferedImage slice;
    private volatile boolean dirty = true;

    public LeaderboardMapRenderer(BufferedImage slice) {
        super(false); // false = render for all players, not context-specific
        this.slice = slice;
    }

    /** Update the image slice and mark the renderer as needing a redraw. */
    public void updateSlice(BufferedImage newSlice) {
        this.slice = newSlice;
        this.dirty = true;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (!dirty) return;
        BufferedImage img = this.slice;
        if (img != null) {
            canvas.drawImage(0, 0, img);
        }
        dirty = false;
    }

    // ─── Static helpers ───────────────────────────────────────────────────────

    /**
     * Installs (or updates) a {@link LeaderboardMapRenderer} on every MapView
     * that backs the given {@link ImageMap}.
     *
     * ImageFrame maps expose their underlying MapViews via {@link ImageMap#getMapViews()}.
     * The full image is cropped into 128×128 tiles in row-major order matching
     * the ImageFrame tile layout (left→right, top→bottom).
     *
     * @param imageMap The target ImageFrame map
     * @param fullImage The full rendered leaderboard image (pixelWidth × pixelHeight)
     */
    public static void installOn(ImageMap imageMap, BufferedImage fullImage) {
        List<MapView> mapViews = imageMap.getMapViews();
        if (mapViews == null || mapViews.isEmpty()) return;

        int cols = imageMap.getWidth();   // tile columns
        // int rows = imageMap.getHeight(); // tile rows (not needed directly)

        for (int i = 0; i < mapViews.size(); i++) {
            MapView view = mapViews.get(i);
            if (view == null) continue;

            int tileCol = i % cols;
            int tileRow = i / cols;
            int srcX    = tileCol * 128;
            int srcY    = tileRow * 128;

            // Crop the correct 128×128 slice (clamp to image bounds)
            int sliceW = Math.min(128, fullImage.getWidth()  - srcX);
            int sliceH = Math.min(128, fullImage.getHeight() - srcY);
            if (sliceW <= 0 || sliceH <= 0) continue;

            BufferedImage slice = fullImage.getSubimage(srcX, srcY, sliceW, sliceH);

            // Find an existing LeaderboardMapRenderer on this view, or add a new one
            LeaderboardMapRenderer existing = null;
            for (MapRenderer r : view.getRenderers()) {
                if (r instanceof LeaderboardMapRenderer lmr) {
                    existing = lmr;
                    break;
                }
            }

            if (existing != null) {
                existing.updateSlice(slice);
            } else {
                // Remove ImageFrame's own renderers so we take full control of the canvas
                view.getRenderers().forEach(view::removeRenderer);
                view.addRenderer(new LeaderboardMapRenderer(slice));
            }
        }
    }
}