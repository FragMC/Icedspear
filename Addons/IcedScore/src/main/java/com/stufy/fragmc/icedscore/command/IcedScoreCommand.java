package com.stufy.fragmc.icedscore.command;

import com.stufy.fragmc.icedscore.IcedScores;
import com.stufy.fragmc.icedscore.config.LeaderboardConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IcedScoreCommand implements CommandExecutor, TabCompleter {

    private final IcedScores plugin;

    public IcedScoreCommand(IcedScores plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (!sender.hasPermission("icedscore.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) { sendHelp(sender, label); return true; }

        return switch (args[0].toLowerCase()) {
            case "help"    -> { sendHelp(sender, label); yield true; }
            case "list"    -> { cmdList(sender);         yield true; }
            case "reload"  -> { cmdReload(sender);       yield true; }
            case "refresh" -> { cmdRefresh(sender, args);yield true; }
            case "info"    -> { cmdInfo(sender, args);   yield true; }
            default        -> { sendHelp(sender, label); yield true; }
        };
    }

    // ─── Subcommands ─────────────────────────────────────────────────────────

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage(Component.text("═══ IcedScores Help ═══", NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        help(s, label, "help",         "Show this help");
        help(s, label, "list",         "List all configured leaderboards");
        help(s, label, "info <id>",    "Show settings for a leaderboard");
        help(s, label, "refresh [id]", "Force refresh (all or specific)");
        help(s, label, "reload",       "Reload config and restart tasks");
    }

    private void help(CommandSender s, String label, String sub, String desc) {
        s.sendMessage(Component.text("/" + label + " " + sub, NamedTextColor.YELLOW)
                .append(Component.text(" — " + desc, NamedTextColor.GRAY)));
    }

    private void cmdList(CommandSender s) {
        Map<String, LeaderboardConfig> lbs = plugin.getLeaderboards();
        if (lbs.isEmpty()) {
            s.sendMessage(Component.text("No leaderboards configured.", NamedTextColor.GRAY));
            return;
        }
        s.sendMessage(Component.text("Configured leaderboards (" + lbs.size() + "):", NamedTextColor.AQUA));
        for (Map.Entry<String, LeaderboardConfig> e : lbs.entrySet()) {
            LeaderboardConfig cfg = e.getValue();
            s.sendMessage(Component.text("  • ", NamedTextColor.DARK_AQUA)
                    .append(Component.text(e.getKey(), NamedTextColor.WHITE))
                    .append(Component.text(" → map: ", NamedTextColor.GRAY))
                    .append(Component.text(cfg.mapName, NamedTextColor.YELLOW))
                    .append(Component.text(" | frame: ", NamedTextColor.GRAY))
                    .append(Component.text(cfg.imageFrameMap, NamedTextColor.GREEN))
                    .append(Component.text(" | size: " + cfg.mapWidth + "x" + cfg.mapHeight, NamedTextColor.GRAY)));
        }
    }

    private void cmdReload(CommandSender s) {
        try {
            plugin.loadAll();
            s.sendMessage(Component.text("IcedScores reloaded! "
                    + plugin.getLeaderboards().size() + " leaderboard(s) loaded.", NamedTextColor.GREEN));
        } catch (Exception e) {
            s.sendMessage(Component.text("Reload failed: " + e.getMessage(), NamedTextColor.RED));
            plugin.getLogger().severe("Reload failed: " + e.getMessage());
        }
    }

    private void cmdRefresh(CommandSender s, String[] args) {
        if (args.length >= 2) {
            String id = args[1];
            if (!plugin.getLeaderboards().containsKey(id)) {
                s.sendMessage(Component.text("Unknown leaderboard: " + id, NamedTextColor.RED));
                return;
            }
            plugin.refreshSingle(id);
            s.sendMessage(Component.text("Refreshed leaderboard: " + id, NamedTextColor.GREEN));
        } else {
            plugin.refreshAll();
            s.sendMessage(Component.text("Refreshed all "
                    + plugin.getLeaderboards().size() + " leaderboard(s).", NamedTextColor.GREEN));
        }
    }

    private void cmdInfo(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Component.text("Usage: /icedscore info <id>", NamedTextColor.RED));
            return;
        }
        String id = args[1];
        LeaderboardConfig cfg = plugin.getLeaderboards().get(id);
        if (cfg == null) {
            s.sendMessage(Component.text("Unknown leaderboard: " + id, NamedTextColor.RED));
            return;
        }
        s.sendMessage(Component.text("─── " + id + " ───", NamedTextColor.AQUA));
        info(s, "IcedSpear map",   cfg.mapName);
        info(s, "ImageFrame name", cfg.imageFrameMap);
        info(s, "Map size",        cfg.mapWidth + "x" + cfg.mapHeight
                + " tiles (" + cfg.pixelWidth() + "x" + cfg.pixelHeight() + "px)");
        info(s, "Entries",         String.valueOf(cfg.entries));
        info(s, "Title",           cfg.titleText + " (" + cfg.titleFont + " " + cfg.titleFontSize + "pt)");
        info(s, "Podium",          cfg.podiumEnabled ? "enabled" : "disabled");
        info(s, "Gradient bg",     cfg.gradientEnabled ? "enabled" : "disabled");
        info(s, "Border",          cfg.borderEnabled
                ? cfg.borderThickness + "px, radius=" + cfg.borderCornerRadius : "disabled");
    }

    private void info(CommandSender s, String key, String val) {
        s.sendMessage(Component.text("  " + key + ": ", NamedTextColor.GRAY)
                .append(Component.text(val, NamedTextColor.WHITE)));
    }

    // ─── Tab completion ──────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1)
            return Arrays.asList("help", "list", "reload", "refresh", "info");
        if (args.length == 2 && (args[0].equalsIgnoreCase("refresh") || args[0].equalsIgnoreCase("info")))
            return plugin.getLeaderboards().keySet().stream()
                    .filter(id -> id.startsWith(args[1]))
                    .collect(Collectors.toList());
        return List.of();
    }
}