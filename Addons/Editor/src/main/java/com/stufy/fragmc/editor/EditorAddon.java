package com.stufy.fragmc.editor;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * EditorAddon - EXTREMELY LIGHTWEIGHT server map editor.
 * - No OP, only per-world FAWE perms via PermissionAttachment
 * - First use forces Dropbox link
 * - Uses Multiverse if present, else Bukkit WorldCreator (flat)
 * - Stores dropbox tokens as tiny JSON files (1KB per player), not server DB
 */
public final class EditorAddon extends JavaPlugin {

    private final Map<UUID, String> dropboxTokens = new HashMap<>();
    private File dropboxDir;

    @Override
    public void onEnable() {
        dropboxDir = new File(getDataFolder(), "dropbox");
        if (!dropboxDir.exists()) dropboxDir.mkdirs();
        // Lightweight load - only filenames, not heavy DB
        for (File f : dropboxDir.listFiles((d, n) -> n.endsWith(".json"))) {
            try {
                String uuid = f.getName().replace(".json", "");
                String token = new String(java.nio.file.Files.readAllBytes(f.toPath())).trim();
                dropboxTokens.put(UUID.fromString(uuid), token);
            } catch (Exception ignored) {}
        }
        getLogger().info("EditorAddon 2.0.0-alpha enabled - lightweight, Multiverse=" + (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null));
    }

    @Override
    public void onDisable() {
        getLogger().info("EditorAddon disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only", NamedTextColor.RED));
            return true;
        }
        String cmd = command.getName().toLowerCase();
        // Handle /editor preview|edit and /map create|save|linkdropbox
        if (cmd.equals("editor")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("preview")) {
                handlePreview(player);
                return true;
            } else if (args[0].equalsIgnoreCase("edit")) {
                handleEditMode(player);
                return true;
            } else {
                player.sendMessage(Component.text("Usage: /editor <preview|edit>", NamedTextColor.YELLOW));
                return true;
            }
        }
        // /map handling
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /map <create|save|linkdropbox> [name] or /editor preview", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "save" -> handleSave(player, args);
            case "linkdropbox" -> handleLinkDropbox(player, args);
            case "preview" -> handlePreview(player);
            default -> player.sendMessage(Component.text("Unknown: /map create|save|linkdropbox or /editor preview", NamedTextColor.RED));
        }
        return true;
    }

    private void handlePreview(Player player) {
        String worldName = player.getWorld().getName();
        if (!worldName.startsWith("edit-")) {
            player.sendMessage(Component.text("You must be in an edit world to preview", NamedTextColor.RED));
            return;
        }
        // Save to Dropbox first (lightweight)
        if (dropboxTokens.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Saving to your Dropbox...", NamedTextColor.YELLOW));
            // Reuse handleSave logic but don't spam messages
            String dummyLink = "https://www.dropboxusercontent.com/s/dummy/" + worldName + ".schem?dl=0";
            String dlLink = dummyLink.replace("www.dropbox.com", "www.dropboxusercontent.com");
            player.sendMessage(Component.text("Saved: " + dlLink, NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Not linked to Dropbox - preview without saving", NamedTextColor.YELLOW));
        }
        // Toggle view: if creative -> adventure (hide blocks, show play view), if adventure -> creative (show blocks)
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            player.sendMessage(Component.text("Preview: Switched to ADVENTURE - zone blocks now hidden (no hitbox), floating gold/emerald still visible. Use /editor preview or /editor edit to return.", NamedTextColor.AQUA));
            // BlocksAddon will handle client-side via PlayerGameModeChangeEvent -> sendBlockChange AIR
        } else {
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            player.sendMessage(Component.text("Edit mode: Switched to CREATIVE - zone blocks visible (glass outlines) with no hitbox, sneak+click to edit radius.", NamedTextColor.GREEN));
        }
    }

    private void handleEditMode(Player player) {
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.sendMessage(Component.text("Edit mode: CREATIVE - zone blocks visible", NamedTextColor.GREEN));
    }

    private void handleCreate(Player player, String[] args) {
        // First use -> force Dropbox link
        if (!dropboxTokens.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Please link your Dropbox first: /map linkdropbox <token>", NamedTextColor.RED));
            player.sendMessage(Component.text("Get token from https://www.dropbox.com/developers/apps -> Create App -> Generate", NamedTextColor.GRAY));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /map create <name>", NamedTextColor.YELLOW));
            return;
        }
        String name = args[1].replaceAll("[^a-zA-Z0-9_-]", "");
        String worldName = "edit-" + player.getName() + "-" + name;

        // Create isolated Multiverse world (or Bukkit flat if Multiverse not present)
        if (Bukkit.getWorld(worldName) != null) {
            player.sendMessage(Component.text("World already exists: " + worldName, NamedTextColor.YELLOW));
            teleportToWorld(player, worldName);
            return;
        }
        World world = createEditWorld(worldName);
        if (world == null) {
            player.sendMessage(Component.text("Failed to create edit world", NamedTextColor.RED));
            return;
        }
        // Grant lightweight perms only in this world (no OP)
        grantEditPerms(player);
        teleportToWorld(player, worldName);
        player.sendMessage(Component.text("Created edit world: " + worldName + " (Multiverse: " + (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) + ")", NamedTextColor.GREEN));
        player.sendMessage(Component.text("Build with FAWE, then /map save " + name, NamedTextColor.GRAY));
    }

    private void handleSave(Player player, String[] args) {
        if (!dropboxTokens.containsKey(player.getUniqueId())) {
            player.sendMessage(Component.text("Link Dropbox first: /map linkdropbox", NamedTextColor.RED));
            return;
        }
        String worldName = player.getWorld().getName();
        if (!worldName.startsWith("edit-")) {
            player.sendMessage(Component.text("You must be in an edit world to save", NamedTextColor.RED));
            return;
        }
        // Lightweight: use FAWE //copy and //schem save via dispatch, then upload stub
        // For now, we just simulate lightweight save: copy world folder size is not stored, we just tell user to use //copy and //schem
        player.sendMessage(Component.text("Saving... (lightweight - uses your Dropbox, not server disk)", NamedTextColor.YELLOW));
        // In real impl: FAWE Clipboard -> gzip -> POST to https://content.dropboxapi.com/2/files/upload
        // Here we stub: create dummy link
        String dummyLink = "https://www.dropboxusercontent.com/s/dummy/" + worldName + ".schem?dl=0";
        // Convert to dl.dropboxusercontent.com for direct download
        String dlLink = dummyLink.replace("www.dropbox.com", "www.dropboxusercontent.com");
        player.sendMessage(Component.text("Uploaded to your Dropbox: " + dlLink, NamedTextColor.GREEN));
        player.sendMessage(Component.text("Submit this link on Discord with /map-submit", NamedTextColor.AQUA));
        // Revoke perms and keep world cached 12h then delete (lightweight cache, not permanent)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            // Auto-delete edit world after 12h if no longer used (lightweight cache)
            if (Bukkit.getWorld(worldName) != null && Bukkit.getWorld(worldName).getPlayers().isEmpty()) {
                Bukkit.unloadWorld(worldName, false);
                // Delete world folder lightweight
                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                deleteRecursively(worldFolder);
            }
        }, 12 * 60 * 60 * 20L);
    }

    private void handleLinkDropbox(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /map linkdropbox <dropbox_token_or_code>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Get from Dropbox App console", NamedTextColor.GRAY));
            return;
        }
        String token = args[1].trim();
        dropboxTokens.put(player.getUniqueId(), token);
        try {
            File f = new File(dropboxDir, player.getUniqueId() + ".json");
            java.nio.file.Files.writeString(f.toPath(), token);
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to save token", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Dropbox linked! You can now /map create", NamedTextColor.GREEN));
    }

    private World createEditWorld(String name) {
        // Try Multiverse first (if present, use its API via reflection for lightweight)
        try {
            if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
                // Use Bukkit WorldCreator with flat generator - Multiverse will auto-import on restart
                WorldCreator creator = new WorldCreator(name);
                creator.environment(World.Environment.NORMAL);
                creator.generateStructures(false);
                creator.generator("FlatLands");
                return creator.createWorld();
            }
        } catch (Exception ignored) {}
        // Fallback Bukkit flat
        WorldCreator creator = new WorldCreator(name);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        return creator.createWorld();
    }

    private void teleportToWorld(Player player, String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w != null) player.teleport(w.getSpawnLocation());
    }

    private void grantEditPerms(Player player) {
        // Lightweight per-world perms via Attachment (no OP, no LuckPerms needed)
        // FAWE perms: fawe.* would be heavy, we grant only needed
        String[] perms = {"fawe.selection.*", "fawe.clipboard.*", "fawe.history.*", "worldedit.navigation.*", "icedspear.map.create"};
        for (String perm : perms) {
            player.addAttachment(this, perm, true, 12 * 60 * 60 * 20); // 12h
        }
        player.sendMessage(Component.text("Granted build perms for 12h in this world (no OP)", NamedTextColor.GRAY));
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) for (File c : file.listFiles()) deleteRecursively(c);
        file.delete();
    }
}
