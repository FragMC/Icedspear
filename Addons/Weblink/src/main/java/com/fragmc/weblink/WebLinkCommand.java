package com.fragmc.weblink;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WebLinkCommand implements CommandExecutor, TabCompleter {
    private final WebLinkAddon plugin;

    public WebLinkCommand(WebLinkAddon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "link":
                handleLink(player);
                break;

            case "unlink":
                handleUnlink(player);
                break;

            case "status":
                handleStatus(player);
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleLink(Player player) {
        if (plugin.getLinkManager().isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Your account is already linked!");
            player.sendMessage(ChatColor.YELLOW + "Use " + ChatColor.WHITE + "/weblink unlink" +
                    ChatColor.YELLOW + " to unlink first.");
            return;
        }

        String code = plugin.getLinkManager().generateLinkCode(player);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.AQUA + "       Web Account Linking");
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + "Your link code is: " + ChatColor.GREEN + ChatColor.BOLD + code);
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "1. Go to the website and log in");
        player.sendMessage(ChatColor.GRAY + "2. Navigate to the account linking page");
        player.sendMessage(ChatColor.GRAY + "3. Enter the code above");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "⚠ This code will expire in 2 minutes!");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    private void handleUnlink(Player player) {
        if (!plugin.getLinkManager().isLinked(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Your account is not linked!");
            return;
        }

        boolean success = plugin.getLinkManager().unlinkAccount(player.getUniqueId());
        if (success) {
            player.sendMessage(ChatColor.GREEN + "✓ Your account has been unlinked successfully!");
        } else {
            player.sendMessage(ChatColor.RED + "Failed to unlink your account. Please try again.");
        }
    }

    private void handleStatus(Player player) {
        boolean isLinked = plugin.getLinkManager().isLinked(player.getUniqueId());

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.AQUA + "       Web Link Status");
        player.sendMessage("");

        if (isLinked) {
            player.sendMessage(ChatColor.GREEN + "✓ Your account is linked!");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "You can now control your game from the website.");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/weblink unlink" +
                    ChatColor.GRAY + " to remove the link.");
        } else {
            player.sendMessage(ChatColor.RED + "✗ Your account is not linked.");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/weblink link" +
                    ChatColor.GRAY + " to get started!");
        }

        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    private void sendHelp(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.AQUA + "       WebLink Commands");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "/weblink link " + ChatColor.GRAY + "- Get a code to link your account");
        player.sendMessage(ChatColor.YELLOW + "/weblink unlink " + ChatColor.GRAY + "- Unlink your account");
        player.sendMessage(ChatColor.YELLOW + "/weblink status " + ChatColor.GRAY + "- Check link status");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("link", "unlink", "status");
            for (String sub : subcommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        }

        return completions;
    }
}