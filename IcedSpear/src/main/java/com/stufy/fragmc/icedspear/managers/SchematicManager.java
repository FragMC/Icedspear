package com.stufy.fragmc.icedspear.managers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.stufy.fragmc.icedspear.IcedSpear;
import org.bukkit.Location;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.util.BoundingBox;

public class SchematicManager {
    private final IcedSpear plugin;
    private final File schematicsFolder;
    private final Map<String, String> mapToSchematic;
    private JsonObject mapData;

    public SchematicManager(IcedSpear plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");
        this.mapToSchematic = new HashMap<>();

        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }

        loadMapData();
    }

    public void loadMapData() {
        String jsonUrl = plugin.getConfig().getString("map-data-url", "");

        if (jsonUrl.isEmpty()) {
            plugin.getLogger().warning("No map-data-url configured!");
            return;
        }

        try {
            plugin.getLogger().info("Loading map data from: " + jsonUrl);

            URL url = new URL(jsonUrl);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true); // Follow redirects (http -> https)
            connection.setRequestProperty("User-Agent", "IcedSpear/1.0"); // Some servers require user agent
            connection.connect();

            int responseCode = connection.getResponseCode();
            plugin.getLogger().info("HTTP Response Code: " + responseCode);

            if (responseCode == 301 || responseCode == 302) {
                String newUrl = connection.getHeaderField("Location");
                plugin.getLogger().info("Redirected to: " + newUrl);
                connection = (java.net.HttpURLConnection) new URL(newUrl).openConnection();
                connection.connect();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder json = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            reader.close();

            String jsonString = json.toString();
            plugin.getLogger().info("Received JSON (first 200 chars): " +
                    jsonString.substring(0, Math.min(200, jsonString.length())));

            // Check if response is HTML (error page)
            if (jsonString.trim().startsWith("<")) {
                plugin.getLogger().severe("URL returned HTML instead of JSON! Check if the URL is correct.");
                plugin.getLogger().severe("Response: " + jsonString.substring(0, Math.min(500, jsonString.length())));
                return;
            }

            Gson gson = new Gson();
            mapData = gson.fromJson(jsonString, JsonObject.class);

            if (mapData == null) {
                plugin.getLogger().severe("Failed to parse JSON - mapData is null");
                return;
            }

            // Parse map data
            mapToSchematic.clear();
            for (String mapName : mapData.keySet()) {
                JsonObject mapInfo = mapData.getAsJsonObject(mapName);
                if (mapInfo.has("schematic")) {
                    String schematic = mapInfo.get("schematic").getAsString();
                    mapToSchematic.put(mapName, schematic);
                    plugin.getLogger().info("Loaded map: " + mapName + " -> " + schematic);
                }
            }

            plugin.getLogger().info("Successfully loaded " + mapToSchematic.size() + " maps from external JSON");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load map data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean pasteSchematic(String schematicName, Location location) {
        File schematicFile = new File(schematicsFolder, schematicName + ".schem");

        if (!schematicFile.exists()) {
            schematicFile = new File(schematicsFolder, schematicName + ".schematic");
        }

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic file not found: " + schematicName);
            plugin.getLogger().warning("Searched in: " + schematicsFolder.getAbsolutePath());
            plugin.getLogger().warning("Looking for: " + schematicName + ".schem or " + schematicName + ".schematic");

            // List available schematics
            File[] files = schematicsFolder.listFiles();
            if (files != null && files.length > 0) {
                plugin.getLogger().warning("Available schematics:");
                for (File file : files) {
                    plugin.getLogger().warning("  - " + file.getName());
                }
            } else {
                plugin.getLogger().warning("Schematics folder is empty!");
            }

            return false;
        }

        plugin.getLogger().info("Found schematic file: " + schematicFile.getName());

        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);

            if (format == null) {
                plugin.getLogger().warning("Unknown schematic format: " + schematicName);
                return false;
            }

            plugin.getLogger().info("Using format: " + format.getName());

            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                Clipboard clipboard = reader.read();

                // Get dimensions using the region
                BlockVector3 min = clipboard.getMinimumPoint();
                BlockVector3 max = clipboard.getMaximumPoint();
                int width = max.x() - min.x() + 1;
                int height = max.y() - min.y() + 1;
                int length = max.z() - min.z() + 1;

                plugin.getLogger().info("Clipboard loaded, dimensions: " +
                        width + "x" + height + "x" + length);

                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                    editSession.setFastMode(false); // Disable fast mode to ensure proper pasting

                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(location.getX(), location.getY(), location.getZ()))
                            .ignoreAirBlocks(false)
                            .copyEntities(true) // Copy entities from schematic
                            .copyBiomes(false) // Don't copy biomes (can cause issues)
                            .build();

                    Operations.complete(operation);

                    plugin.getLogger().info("Schematic pasted successfully at " +
                            location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
                }

                // Give the world a moment to process the paste
                Thread.sleep(100);

                return true;
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to paste schematic: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public org.bukkit.util.BoundingBox getSchematicBounds(String schematicName, Location pasteLocation) {
        File schematicFile = new File(schematicsFolder, schematicName + ".schem");
        if (!schematicFile.exists()) {
            schematicFile = new File(schematicsFolder, schematicName + ".schematic");
        }

        if (!schematicFile.exists()) {
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            return null;
        }

        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
            Clipboard clipboard = reader.read();

            BlockVector3 origin = clipboard.getOrigin();
            com.sk89q.worldedit.regions.Region region = clipboard.getRegion();
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            BlockVector3 offset = BlockVector3.at(pasteLocation.getX(), pasteLocation.getY(), pasteLocation.getZ())
                    .subtract(origin);
            BlockVector3 pastedMin = min.add(offset);
            BlockVector3 pastedMax = max.add(offset);

            return new org.bukkit.util.BoundingBox(pastedMin.x(), pastedMin.y(), pastedMin.z(), pastedMax.x(),
                    pastedMax.y(), pastedMax.z());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to get schematic bounds: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean removeSchematic(String schematicName) {
        File schematicFile = new File(schematicsFolder, schematicName + ".schem");

        if (!schematicFile.exists()) {
            schematicFile = new File(schematicsFolder, schematicName + ".schematic");
        }

        if (!schematicFile.exists()) {
            plugin.getLogger().warning("Schematic file not found: " + schematicName);
            return false;
        }

        boolean deleted = schematicFile.delete();

        if (deleted) {
            plugin.getLogger().info("Deleted schematic: " + schematicName);
        } else {
            plugin.getLogger().warning("Failed to delete schematic: " + schematicName);
        }

        return deleted;
    }

    public boolean importSchematic(String worldEditName, String targetName) {
        // Try FAWE location first
        File weFolder = new File(plugin.getDataFolder().getParentFile(), "FastAsyncWorldEdit/schematics");
        File sourceFile = new File(weFolder, worldEditName + ".schem");

        if (!sourceFile.exists()) {
            sourceFile = new File(weFolder, worldEditName + ".schematic");
        }

        // Fallback to WorldEdit location if not found
        if (!sourceFile.exists()) {
            weFolder = new File(plugin.getServer().getWorldContainer(), "plugins/WorldEdit/schematics");
            sourceFile = new File(weFolder, worldEditName + ".schem");

            if (!sourceFile.exists()) {
                sourceFile = new File(weFolder, worldEditName + ".schematic");
            }
        }

        if (!sourceFile.exists()) {
            plugin.getLogger().warning("Schematic not found in FAWE or WorldEdit folders: " + worldEditName);
            plugin.getLogger().warning("Checked: plugins/FastAsyncWorldEdit/schematics/ and plugins/WorldEdit/schematics/");
            return false;
        }

        File targetFile = new File(schematicsFolder,
                targetName + sourceFile.getName().substring(worldEditName.length()));

        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath());
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to import schematic: " + e.getMessage());
            return false;
        }
    }

    public String getSchematicForMap(String mapName) {
        return mapToSchematic.get(mapName);
    }

    public JsonObject getMapData() {
        return mapData;
    }

    public Map<String, String> getAllMaps() {
        return new HashMap<>(mapToSchematic);
    }
}