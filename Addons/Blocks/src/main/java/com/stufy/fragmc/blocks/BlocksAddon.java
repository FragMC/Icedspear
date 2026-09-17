package com.stufy.fragmc.blocks;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * BlocksAddon - Smart zone blocks.
 * Requires EditorAddon (depend) - lightweight server editor must be present.
 * Auto-installs FMM models to FreeMinecraftModels/models/fragmc_zones/ on first enable.
 * When map is opened (PLAYING), blocks are hidden/no hitbox; during EDITING visible as glass.
 */
public final class BlocksAddon extends JavaPlugin implements Listener {

    private final NamespacedKey zoneTypeKey = new NamespacedKey(this, "zone_type");
    private final NamespacedKey zoneRadiusKey = new NamespacedKey(this, "zone_radius");

    @Override
    public void onEnable() {
        // Verify EditorAddon is present (depend ensures, but double-check for lightweight)
        if (Bukkit.getPluginManager().getPlugin("EditorAddon") == null) {
            getLogger().severe("EditorAddon not found! BlocksAddon requires EditorAddon - disabling");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        // Auto-install FMM models (dummy files for now, user will replace textures)
        installFmmModels();
        // Register zone block listener (sneak+click UI)
        Bukkit.getPluginManager().registerEvents(this, this);
        // Register zone items (give commands etc. could be added)
        getLogger().info("BlocksAddon 2.0.0-alpha enabled - requires EditorAddon, FMM models auto-installed (dummy, replace textures)");
    }

    private void installFmmModels() {
        try {
            File fmmModelsDir = new File("plugins/FreeMinecraftModels/models/fragmc_zones");
            if (!fmmModelsDir.exists()) fmmModelsDir.mkdirs();
            // Dummy files from resources/fmm/models/
            String[] models = {"elytra_zone.bbmodel", "remove_elytra_zone.bbmodel", "checkpoint_return_zone.bbmodel"};
            for (String model : models) {
                File target = new File(fmmModelsDir, model);
                if (target.exists()) continue; // don't overwrite existing (user may have real textures)
                try (InputStream in = getResource("fmm/models/" + model)) {
                    if (in != null) {
                        Files.copy(in, target.toPath());
                        getLogger().info("Auto-installed FMM model: " + model + " -> " + target.getPath() + " (dummy, replace with real Blockbench export)");
                    }
                }
            }
            // Also create dummy png placeholders so FMM doesn't error on missing texture
            for (String png : new String[]{"elytra_zone.png", "remove_elytra_zone.png", "checkpoint_return_zone.png"}) {
                File targetPng = new File(fmmModelsDir, png);
                if (!targetPng.exists()) {
                    // Create 1x1 transparent dummy (or just empty file for now, FMM will warn but not crash)
                    targetPng.createNewFile();
                }
            }
            getLogger().info("FMM models installed to " + fmmModelsDir.getPath() + " - run /fmm reload after replacing dummy textures");
        } catch (Exception e) {
            getLogger().warning("Failed to auto-install FMM models: " + e.getMessage());
        }
    }

    @EventHandler
    public void onZoneBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getClickedBlock() == null) return;
        // For lightweight, we store zone data on ItemStack when placed
        // Simplified: check held item is zone block
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (hand == null || !hand.hasItemMeta()) return;
        String zoneType = hand.getItemMeta().getPersistentDataContainer().get(zoneTypeKey, PersistentDataType.STRING);
        if (zoneType == null) return;
        event.setCancelled(true);
        // Open chest UI for Java, SimpleForm for Bedrock (via Floodgate check)
        openZoneUi(event.getPlayer(), zoneType, hand);
    }

    private void openZoneUi(Player player, String zoneType, ItemStack item) {
        boolean isBedrock = false;
        try {
            if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
                Object api = Class.forName("org.geysermc.floodgate.api.FloodgateApi").getMethod("getInstance").invoke(null);
                if (api != null) {
                    isBedrock = (boolean) Class.forName("org.geysermc.floodgate.api.FloodgateApi").getMethod("isFloodgatePlayer", java.util.UUID.class).invoke(api, player.getUniqueId());
                }
            }
        } catch (Exception ignored) {}

        Integer radius = item.getItemMeta().getPersistentDataContainer().get(zoneRadiusKey, PersistentDataType.INTEGER);
        if (radius == null) radius = 5;

        if (isBedrock) {
            // Bedrock SimpleForm
            try {
                Class<?> formClass = Class.forName("org.geysermc.cumulus.form.CustomForm");
                // For lightweight, just send message
                player.sendMessage(Component.text("Bedrock Zone UI: " + zoneType + " radius=" + radius + " (use /zone radius <1-32>)", NamedTextColor.AQUA));
            } catch (Exception e) {
                player.sendMessage(Component.text("Zone: " + zoneType + " radius: " + radius, NamedTextColor.AQUA));
            }
        } else {
            // Java chest UI 27 with gray glass filler, radius slider
            var inv = Bukkit.createInventory(null, 27, Component.text("Zone: " + zoneType, NamedTextColor.AQUA));
            ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            var fillerMeta = filler.getItemMeta();
            fillerMeta.displayName(Component.text(" ", NamedTextColor.GRAY));
            filler.setItemMeta(fillerMeta);
            for (int i = 0; i < 27; i++) inv.setItem(i, filler);
            // Radius display in center
            ItemStack radiusItem = new ItemStack(Material.CLOCK);
            var meta = radiusItem.getItemMeta();
            meta.displayName(Component.text("Radius: " + radius, NamedTextColor.YELLOW));
            meta.lore(java.util.List.of(Component.text("Click +/- to change 1-32", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(zoneRadiusKey, PersistentDataType.INTEGER, radius);
            radiusItem.setItemMeta(meta);
            inv.setItem(13, radiusItem);
            // +/- buttons
            ItemStack minus = new ItemStack(Material.RED_CONCRETE);
            var mMeta = minus.getItemMeta();
            mMeta.displayName(Component.text("-1", NamedTextColor.RED));
            minus.setItemMeta(mMeta);
            inv.setItem(11, minus);
            ItemStack plus = new ItemStack(Material.LIME_CONCRETE);
            var pMeta = plus.getItemMeta();
            pMeta.displayName(Component.text("+1", NamedTextColor.GREEN));
            plus.setItemMeta(pMeta);
            inv.setItem(15, plus);
            player.openInventory(inv);
        }
    }

    public static ItemStack createZoneItem(String zoneType, int radius) {
        Material mat = switch (zoneType) {
            case "elytra_add" -> Material.GLASS;
            case "remove_elytra" -> Material.RED_STAINED_GLASS;
            case "checkpoint_return" -> Material.EMERALD_BLOCK;
            default -> Material.STONE;
        };
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(zoneType, NamedTextColor.AQUA));
        meta.getPersistentDataContainer().set(new NamespacedKey("blocksaddon", "zone_type"), PersistentDataType.STRING, zoneType);
        meta.getPersistentDataContainer().set(new NamespacedKey("blocksaddon", "zone_radius"), PersistentDataType.INTEGER, radius);
        meta.lore(java.util.List.of(Component.text("Radius: " + radius, NamedTextColor.GRAY), Component.text("Sneak+Click to edit", NamedTextColor.DARK_GRAY)));
        item.setItemMeta(meta);
        return item;
    }
}
