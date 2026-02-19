package com.stufy.fragmc.icedscore.render;

import com.stufy.fragmc.icedscore.IcedScores;

import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads TrueType fonts from plugins/IcedScores/fonts/ and caches them.
 * Falls back to a system sans-serif if a font file is missing.
 */
public class FontRegistry {

    private final File fontsDir;
    private final Map<String, Font> baseCache = new HashMap<>();

    public FontRegistry(File fontsDir) {
        this.fontsDir = fontsDir;
    }

    public void reload() {
        baseCache.clear();
    }

    /**
     * Retrieve a font by registered name and size/style.
     */
    public Font get(String name, int size, boolean bold, boolean italic) {
        Font base = baseCache.computeIfAbsent(name, this::loadFont);
        int style = Font.PLAIN;
        if (bold)   style |= Font.BOLD;
        if (italic) style |= Font.ITALIC;
        return base.deriveFont(style, (float) size);
    }

    public Font get(String name, int size) {
        return get(name, size, false, false);
    }

    // ─── Internal loading ────────────────────────────────────────────────────

    private Font loadFont(String name) {
        String fileName = IcedScores.getInstance()
                .getConfig()
                .getString("fonts." + name, name + ".ttf");

        File file = new File(fontsDir, fileName);
        if (file.exists()) {
            try (InputStream in = file.toURI().toURL().openStream()) {
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            } catch (Exception e) {
                IcedScores.getInstance().getLogger().log(Level.WARNING,
                        "Could not load font '" + name + "' from " + file.getPath(), e);
            }
        } else {
            IcedScores.getInstance().getLogger().warning(
                    "Font file not found: " + file.getPath() + " — using fallback.");
        }
        return new Font("SansSerif", Font.PLAIN, 12);
    }
}