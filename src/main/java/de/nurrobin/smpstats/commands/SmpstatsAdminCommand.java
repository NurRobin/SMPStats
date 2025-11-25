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
        if (args.length == 0) {
            showInfo(sender);
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!hasReloadPermission(sender)) {
                sender.sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.reload");
                return true;
            }
            plugin.reloadPluginConfig(sender);
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Nutze: /smpstats [info|reload]");
        return true;
    }

    private boolean hasReloadPermission(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true; // console
        }
        return sender.hasPermission("smpstats.reload");
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "╔══════════ " + ChatColor.AQUA + "SMPStats" + ChatColor.DARK_AQUA + " ══════════");
        sender.sendMessage(line("Version", plugin.getDescription().getVersion()));
        sender.sendMessage(line("API", plugin.getSettings().isApiEnabled()
                ? "Enabled @ :" + plugin.getSettings().getApiPort()
                : "Disabled"));
        sender.sendMessage(line("Autosave", plugin.getSettings().getAutosaveMinutes() + " min"));
        sender.sendMessage(line("Moments", plugin.getSettings().isMomentsEnabled() ? "On" : "Off"));
        sender.sendMessage(line("Heatmap", plugin.getSettings().isHeatmapEnabled() ? "On" : "Off"));
        sender.sendMessage(line("Tracking", trackingSummary()));
        sender.sendMessage(ChatColor.DARK_AQUA + "╚═══════════════════════════════");
    }

    private String line(String key, String value) {
        return ChatColor.GRAY + "  • " + ChatColor.AQUA + key + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + value;
    }

    private String trackingSummary() {
        var s = plugin.getSettings();
        StringBuilder b = new StringBuilder();
        appendFlag(b, "Move", s.isTrackMovement());
        appendFlag(b, "Blocks", s.isTrackBlocks());
        appendFlag(b, "Kills", s.isTrackKills());
        appendFlag(b, "Biomes", s.isTrackBiomes());
        appendFlag(b, "Craft", s.isTrackCrafting());
        appendFlag(b, "Dmg", s.isTrackDamage());
        appendFlag(b, "Use", s.isTrackConsumption());
        return b.toString();
    }

    private void appendFlag(StringBuilder b, String label, boolean on) {
        if (!b.isEmpty()) {
            b.append(ChatColor.DARK_GRAY).append(" | ");
        }
        b.append(on ? ChatColor.GREEN : ChatColor.RED).append(label);
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
