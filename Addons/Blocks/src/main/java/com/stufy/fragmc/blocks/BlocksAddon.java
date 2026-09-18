package com.stufy.fragmc.blocks;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BlocksAddon - Smart zone blocks.
 * Requires EditorAddon (depend) - lightweight server editor must be present.
 * Auto-installs FMM models to FreeMinecraftModels/models/fragmc_zones/ on first enable.
 * When map is opened (PLAYING), blocks are hidden/no hitbox; during EDITING visible as glass.
 */
public final class BlocksAddon extends JavaPlugin implements Listener {

    private final NamespacedKey zoneTypeKey = new NamespacedKey(this, "zone_type");
    private final NamespacedKey zoneRadiusKey = new NamespacedKey(this, "zone_radius");
    // Track zone blocks and their armour stands for spinning/bobbing
    private final Map<Location, ArmorStand> armourStands = new HashMap<>();
    private final Map<Location, String> zoneBlocks = new HashMap<>(); // Location -> zoneType

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("EditorAddon") == null) {
            getLogger().severe("EditorAddon not found! BlocksAddon requires EditorAddon - disabling");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        installFmmModels();
        Bukkit.getPluginManager().registerEvents(this, this);
        // Start spinning/bobbing task for armour stands (gold/emerald 2.5 blocks above)
        new BukkitRunnable() {
            double tick = 0;
            @Override
            public void run() {
                tick += 0.05;
                for (Map.Entry<Location, ArmorStand> entry : armourStands.entrySet()) {
                    ArmorStand stand = entry.getValue();
                    if (stand == null || stand.isDead()) continue;
                    // Slow spin
                    stand.setHeadPose(new org.bukkit.util.EulerAngle(0, tick * 0.5, 0));
                    // Bobbing 0.2 blocks up/down
                    Location loc = entry.getKey().clone().add(0, 2.5 + Math.sin(tick) * 0.2, 0);
                    stand.teleport(loc);
                }
            }
        }.runTaskTimer(this, 0L, 1L);
        // Client-side hitbox handling: blocks have no hitbox in editor and play, visible only in creative via sendBlockChange
        getLogger().info("BlocksAddon 2.0.0-alpha enabled - start/checkpoint have no hitbox, armour stands spinning 2.5 blocks above, visible only in creative (client-side)");
    }

    private void installFmmModels() {
        try {
            File fmmModelsDir = new File("plugins/FreeMinecraftModels/models/fragmc_zones");
            if (!fmmModelsDir.exists()) fmmModelsDir.mkdirs();
            // Dummy files from resources/fmm/models/ - now includes start/checkpoint
            String[] models = {"elytra_zone.bbmodel", "remove_elytra_zone.bbmodel", "checkpoint_return_zone.bbmodel", "start_block.bbmodel", "checkpoint_block.bbmodel"};
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
            for (String png : new String[]{"elytra_zone.png", "remove_elytra_zone.png", "checkpoint_return_zone.png", "start_block.png", "checkpoint_block.png"}) {
                File targetPng = new File(fmmModelsDir, png);
                if (!targetPng.exists()) {
                    targetPng.createNewFile();
                }
            }
            getLogger().info("FMM models installed to " + fmmModelsDir.getPath() + " - run /fmm reload after replacing dummy textures");
        } catch (Exception e) {
            getLogger().warning("Failed to auto-install FMM models: " + e.getMessage());
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        if (hand == null || !hand.hasItemMeta()) return;
        String zoneType = hand.getItemMeta().getPersistentDataContainer().get(zoneTypeKey, PersistentDataType.STRING);
        if (zoneType == null) return;
        // All Blocks addon blocks have no hitbox - set to BARRIER with no collision, but make client-side visible only in creative
        Block block = event.getBlockPlaced();
        zoneBlocks.put(block.getLocation(), zoneType);
        // Make server block AIR (no hitbox) and use sendBlockChange for creative view
        Bukkit.getScheduler().runTask(this, () -> {
            block.setType(Material.AIR, false);
            // Show to creative players as visible block, hidden for survival/adventure
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == GameMode.CREATIVE) {
                    Material displayMat = getDisplayMaterial(zoneType);
                    p.sendBlockChange(block.getLocation(), displayMat.createBlockData());
                } else {
                    p.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
                }
            }
            // Spawn floating armour stand 2.5 blocks above for start/checkpoint (and zones for visibility)
            if (zoneType.equals("start") || zoneType.equals("checkpoint") || zoneType.equals("elytra_add") || zoneType.equals("checkpoint_return")) {
                spawnFloatingBlock(block.getLocation(), zoneType);
            }
        });
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (zoneBlocks.containsKey(loc)) {
            String zoneType = zoneBlocks.remove(loc);
            ArmorStand stand = armourStands.remove(loc);
            if (stand != null) stand.remove();
            // Ensure all players see AIR after break
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendBlockChange(loc, Material.AIR.createBlockData());
            }
            getLogger().info("Removed zone block " + zoneType + " at " + loc);
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // Client-side: blocks only visible in CREATIVE, invisible in SURVIVAL/ADVENTURE/SPECTATOR
        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();
        Bukkit.getScheduler().runTask(this, () -> updatePlayerBlockView(player, newMode));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> updatePlayerBlockView(player, player.getGameMode()), 20L);
    }

    private void updatePlayerBlockView(Player player, GameMode mode) {
        boolean isCreative = mode == GameMode.CREATIVE;
        for (Map.Entry<Location, String> entry : zoneBlocks.entrySet()) {
            Location loc = entry.getKey();
            String zoneType = entry.getValue();
            if (isCreative) {
                Material displayMat = getDisplayMaterial(zoneType);
                player.sendBlockChange(loc, displayMat.createBlockData());
            } else {
                // In survival/adventure/spectator - show AIR (no hitbox, invisible) - armour stand still visible for start/checkpoint
                player.sendBlockChange(loc, Material.AIR.createBlockData());
            }
        }
    }

    private Material getDisplayMaterial(String zoneType) {
        return switch (zoneType) {
            case "start" -> Material.GOLD_BLOCK;
            case "checkpoint" -> Material.EMERALD_BLOCK;
            case "elytra_add" -> Material.GLASS;
            case "remove_elytra" -> Material.RED_STAINED_GLASS;
            case "checkpoint_return" -> Material.EMERALD_BLOCK;
            default -> Material.STONE;
        };
    }

    private void spawnFloatingBlock(Location blockLoc, String zoneType) {
        Location standLoc = blockLoc.clone().add(0.5, 2.5, 0.5);
        ArmorStand stand = (ArmorStand) blockLoc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setInvisible(true);
        stand.setMarker(true); // no hitbox
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        Material headMat = zoneType.equals("start") ? Material.GOLD_BLOCK : zoneType.equals("checkpoint") ? Material.EMERALD_BLOCK : Material.GLASS;
        ItemStack head = new ItemStack(headMat);
        stand.setItem(EquipmentSlot.HEAD, head);
        stand.setHeadPose(new org.bukkit.util.EulerAngle(0, 0, 0));
        armourStands.put(blockLoc, stand);
    }

    @EventHandler
    public void onZoneBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getClickedBlock() == null) return;
        // Check if clicked block is a zone block (tracked)
        Location loc = event.getClickedBlock().getLocation();
        String zoneType = zoneBlocks.get(loc);
        // Fallback: check held item
        if (zoneType == null) {
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (hand == null || !hand.hasItemMeta()) return;
            zoneType = hand.getItemMeta().getPersistentDataContainer().get(zoneTypeKey, PersistentDataType.STRING);
            if (zoneType == null) return;
        }
        event.setCancelled(true);
        // Find the item for UI (use hand or create one)
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        String type = zoneType;
        if (hand == null || !hand.hasItemMeta() || hand.getItemMeta().getPersistentDataContainer().get(zoneTypeKey, PersistentDataType.STRING) == null) {
            hand = createZoneItem(type, 5);
        }
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
            case "start" -> Material.GOLD_BLOCK;
            case "checkpoint" -> Material.EMERALD_BLOCK;
            case "elytra_add" -> Material.GLASS;
            case "remove_elytra" -> Material.RED_STAINED_GLASS;
            case "checkpoint_return" -> Material.EMERALD_BLOCK;
            default -> Material.STONE;
        };
        // All Blocks addon blocks have no hitbox - server actually AIR, client shows mat only in creative
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        String displayName = switch (zoneType) {
            case "start" -> "Start Block";
            case "checkpoint" -> "Checkpoint Block";
            case "elytra_add" -> "Elytra Add Zone";
            case "remove_elytra" -> "Remove Elytra Zone";
            case "checkpoint_return" -> "Checkpoint Return Zone";
            default -> zoneType;
        };
        meta.displayName(Component.text(displayName, NamedTextColor.AQUA));
        // Use plugin instance key if available, else fallback
        meta.getPersistentDataContainer().set(new NamespacedKey("blocksaddon", "zone_type"), PersistentDataType.STRING, zoneType);
        meta.getPersistentDataContainer().set(new NamespacedKey("blocksaddon", "zone_radius"), PersistentDataType.INTEGER, radius);
        String hitboxNote = "No hitbox (walk through) - visible only in Creative";
        if (zoneType.equals("start") || zoneType.equals("checkpoint")) {
            hitboxNote = "No hitbox - floating " + (zoneType.equals("start") ? "Gold" : "Emerald") + " 2.5 blocks above, spinning/bobbing";
        }
        meta.lore(java.util.List.of(Component.text("Radius: " + radius, NamedTextColor.GRAY), Component.text(hitboxNote, NamedTextColor.DARK_GRAY), Component.text("Sneak+Click to edit radius", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }
}
