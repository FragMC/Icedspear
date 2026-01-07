package com.stufy.fragmc.icedspear.commands;

import com.stufy.fragmc.icedspear.IcedSpear;
import com.stufy.fragmc.icedspear.managers.ConfigManager;
import com.stufy.fragmc.icedspear.managers.MapManager;
import com.stufy.fragmc.icedspear.managers.SchematicManager;
import com.stufy.fragmc.icedspear.models.MapInstance;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.*;

public class IcedSpearCommand implements CommandExecutor, TabCompleter {
    private final IcedSpear plugin;
    private final MapManager mapManager;
    private final ConfigManager configManager;
    private final SchematicManager schematicManager;

    public IcedSpearCommand(IcedSpear plugin, MapManager mapManager, ConfigManager configManager, SchematicManager schematicManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        this.configManager = configManager;
        this.schematicManager = schematicManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "import":
                if (!sender.hasPermission("icedspear.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /icedspear import <schematic-name>");
                    return true;
                }
                handleImport(sender, args[1]);
                break;

            case "remove":
                if (!sender.hasPermission("icedspear.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /icedspear remove <schematic-name>");
                    return true;
                }
                handleRemove(sender, args[1]);
                break;

            case "list":
                handleList(sender);
                break;

            case "reload":
                if (!sender.hasPermission("icedspear.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                handleReload(sender);
                break;

            case "config":
                if (!sender.hasPermission("icedspear.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                if (args.length < 2) {
                    sendConfigUsage(sender);
                    return true;
                }
                handleConfig(sender, Arrays.copyOfRange(args, 1, args.length));
                break;

            case "block":
                if (!sender.hasPermission("icedspear.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /icedspear block <player|*> <map>");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /icedspear block <player|*> <map>");
                    return true;
                }
                handleBlock(sender, args[1], args[2]);
                break;

            case "unblock":
                if (!sender.hasPermission("icedspear.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /icedspear unblock <player|*> <map>");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /icedspear unblock <player|*> <map>");
                    return true;
                }
                handleUnblock(sender, args[1], args[2]);
                break;

            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void handleImport(CommandSender sender, String schematicName) {
        boolean success = schematicManager.importSchematic(schematicName, schematicName);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Successfully imported schematic: " + schematicName);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to import schematic. Make sure it exists in WorldEdit's schematics folder!");
        }
    }

    private void handleRemove(CommandSender sender, String schematicName) {
        boolean success = schematicManager.removeSchematic(schematicName);

        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Successfully removed schematic: " + schematicName);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to remove schematic. Make sure it exists!");
        }
    }

    private void handleList(CommandSender sender) {
        Map<String, MapInstance> instances = mapManager.getActiveInstances();

        if (instances.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No active lobbies.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Active Lobbies ===");

        for (Map.Entry<String, MapInstance> entry : instances.entrySet()) {
            MapInstance instance = entry.getValue();
            String type = instance.isPublic() ? ChatColor.GREEN + "[PUBLIC]" : ChatColor.YELLOW + "[PRIVATE]";
            int players = instance.getPlayers().size();
            String state = ChatColor.GRAY + instance.getState().name();

            sender.sendMessage(type + " " + ChatColor.WHITE + instance.getMapName() +
                    ChatColor.GRAY + " (" + players + " players) " + state);
            sender.sendMessage(ChatColor.DARK_GRAY + "  Code: " + ChatColor.GRAY + entry.getKey());
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        schematicManager.loadMapData();
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
    }

    private void handleConfig(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("maxplayers")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Current max players: " + configManager.getMaxPlayers());
                return;
            }
            try {
                int maxPlayers = Integer.parseInt(args[1]);
                configManager.setMaxPlayers(maxPlayers);
                sender.sendMessage(ChatColor.GREEN + "Max players set to: " + maxPlayers);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number!");
            }
        } else if (args[0].equalsIgnoreCase("cleanupdelay")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Current cleanup delay: " + configManager.getCleanupDelay() + " seconds");
                return;
            }
            try {
                long seconds = Long.parseLong(args[1]);
                configManager.setCleanupDelay(seconds);
                sender.sendMessage(ChatColor.GREEN + "Cleanup delay set to: " + seconds + " seconds");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid number!");
            }
        } else if (args[0].equalsIgnoreCase("mapurl")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Current map data URL: " + configManager.getMapDataUrl());
                return;
            }
            configManager.setMapDataUrl(args[1]);
            schematicManager.loadMapData();
            sender.sendMessage(ChatColor.GREEN + "Map data URL updated and reloaded!");
        } else {
            sendConfigUsage(sender);
        }
    }

    private void handleBlock(CommandSender sender, String playerName, String mapName) {
        if (playerName.equals("*")) {
            // Block map globally for all players
            configManager.blockMapGlobally(mapName);
            sender.sendMessage(ChatColor.GREEN + "Blocked map '" + mapName + "' for all players");
            sender.sendMessage(ChatColor.GRAY + "Players with icedspear.bypass can still join");
        } else {
            // Block map for specific player
            org.bukkit.OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
            configManager.blockMapForPlayer(player.getUniqueId().toString(), mapName);
            sender.sendMessage(ChatColor.GREEN + "Blocked " + playerName + " from " + mapName);
        }
    }

    private void handleUnblock(CommandSender sender, String playerName, String mapName) {
        if (playerName.equals("*")) {
            // Unblock map globally
            configManager.unblockMapGlobally(mapName);
            sender.sendMessage(ChatColor.GREEN + "Unblocked map '" + mapName + "' for all players");
        } else {
            // Unblock map for specific player
            org.bukkit.OfflinePlayer player = plugin.getServer().getOfflinePlayer(playerName);
            configManager.unblockMapForPlayer(player.getUniqueId().toString(), mapName);
            sender.sendMessage(ChatColor.GREEN + "Unblocked " + playerName + " from " + mapName);
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== IcedSpear Admin Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear import <schematic>" + ChatColor.GRAY + " - Import a WorldEdit schematic");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear remove <schematic>" + ChatColor.GRAY + " - Remove a schematic");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear list" + ChatColor.GRAY + " - List active lobbies");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear reload" + ChatColor.GRAY + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear config" + ChatColor.GRAY + " - Configure plugin settings");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear block <player|*> <map>" + ChatColor.GRAY + " - Block player(s) from map");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear unblock <player|*> <map>" + ChatColor.GRAY + " - Unblock player(s) from map");
        sender.sendMessage(ChatColor.GRAY + "Use * to block/unblock all players");
    }

    private void sendConfigUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Config Commands ===");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear config maxplayers [number]" + ChatColor.GRAY + " - Set max players per map");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear config cleanupdelay [seconds]" + ChatColor.GRAY + " - Set world cleanup delay");
        sender.sendMessage(ChatColor.YELLOW + "/icedspear config mapurl [url]" + ChatColor.GRAY + " - Set map data JSON URL");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("import", "remove", "list", "reload", "config", "block", "unblock");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return Arrays.asList("maxplayers", "cleanupdelay", "mapurl");
        }
        return new ArrayList<>();
    }
}