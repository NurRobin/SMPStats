package de.nurrobin.smpstats.commands;

import de.nurrobin.smpstats.SMPStats;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmpstatsAdminCommand implements CommandExecutor, TabCompleter {
    private final SMPStats plugin;

    public SmpstatsAdminCommand(SMPStats plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.YELLOW + "Nutze: /smpstats reload");
            return true;
        }

        if (!hasReloadPermission(sender)) {
            sender.sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.reload");
            return true;
        }

        plugin.reloadPluginConfig(sender);
        return true;
    }

    private boolean hasReloadPermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true; // console
        }
        return sender.hasPermission("smpstats.reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("reload");
            return options;
        }
        return Collections.emptyList();
    }
}
