package de.nurrobin.smpstats.commands;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SStatsCommand implements CommandExecutor, TabCompleter {
    private final SMPStats plugin;
    private final StatsService statsService;

    public SStatsCommand(SMPStats plugin, StatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Konsole: /sstats user <player>");
                return true;
            }
            StatsFormatter.render(sender, plugin, statsService, player.getUniqueId(), player.getName());
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info" -> {
                showInfo(sender);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("smpstats.reload")) {
                    sender.sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.reload");
                    return true;
                }
                plugin.reloadPluginConfig(sender);
                return true;
            }
            case "user" -> {
                return handleUser(sender, args);
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "Nutze: /sstats [info|reload|user <name> [reset|set <stat> <value>]]");
                return true;
            }
        }
    }

    private boolean handleUser(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Nutze: /sstats user <name> [reset|set <stat> <value>]");
            return true;
        }
        String name = args[1];
        Optional<StatsRecord> target = statsService.getStatsByName(name);
        if (target.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Spieler '" + name + "' nicht gefunden.");
            return true;
        }
        UUID uuid = target.get().getUuid();

        if (args.length == 2) {
            StatsFormatter.render(sender, plugin, statsService, target.get());
            return true;
        }

        String action = args[2].toLowerCase();
        if ("reset".equals(action)) {
            if (!sender.hasPermission("smpstats.edit")) {
                sender.sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.edit");
                return true;
            }
            boolean ok = statsService.resetStats(uuid);
            sender.sendMessage(ok ? ChatColor.GREEN + "Stats für " + name + " zurückgesetzt." : ChatColor.RED + "Konnte Stats nicht zurücksetzen.");
            return true;
        }

        if ("set".equals(action)) {
            if (!sender.hasPermission("smpstats.edit")) {
                sender.sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.edit");
                return true;
            }
            if (args.length < 5) {
                sender.sendMessage(ChatColor.RED + "Nutze: /sstats user <name> set <stat> <value>");
                return true;
            }
            String statKey = args[3];
            String valueRaw = args[4];
            Optional<StatField> field = StatField.fromString(statKey);
            if (field.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Unbekannter Stat: " + statKey);
                return true;
            }
            double value;
            try {
                value = Double.parseDouble(valueRaw);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Wert muss eine Zahl sein.");
                return true;
            }
            boolean ok = statsService.setStat(uuid, field.get(), value);
            sender.sendMessage(ok ? ChatColor.GREEN + "Stat " + field.get().key() + " für " + name + " gesetzt auf " + value : ChatColor.RED + "Konnte Stat nicht setzen.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Nutze: /sstats user <name> [reset|set <stat> <value>]");
        return true;
    }

    private void showInfo(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_AQUA + "╔══════════ " + ChatColor.AQUA + "SMPStats" + ChatColor.DARK_AQUA + " ══════════");
        sender.sendMessage(infoLine("Version", plugin.getDescription().getVersion()));
        sender.sendMessage(infoLine("API", plugin.getSettings().isApiEnabled()
                ? "Enabled @ :" + plugin.getSettings().getApiPort()
                : "Disabled"));
        sender.sendMessage(infoLine("Autosave", plugin.getSettings().getAutosaveMinutes() + " min"));
        sender.sendMessage(infoLine("Moments", plugin.getSettings().isMomentsEnabled() ? "On" : "Off"));
        sender.sendMessage(infoLine("Heatmap", plugin.getSettings().isHeatmapEnabled() ? "On" : "Off"));
        sender.sendMessage(infoLine("Social", plugin.getSettings().isSocialEnabled()
                ? "On (r=" + plugin.getSettings().getSocialNearbyRadius() + ")"
                : "Off"));
        sender.sendMessage(infoLine("Health", plugin.getSettings().isHealthEnabled() ? "On" : "Off"));
        sender.sendMessage(infoLine("Story", plugin.getSettings().isStoryEnabled() ? "On" : "Off"));
        sender.sendMessage(infoLine("Tracking", trackingSummary()));
        sender.sendMessage(ChatColor.DARK_AQUA + "╚═══════════════════════════════");
    }

    private String infoLine(String key, String value) {
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
            List<String> base = new ArrayList<>();
            base.add("info");
            base.add("reload");
            base.add("user");
            return base;
        }
        if (args.length == 2 && "user".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<>(statsService.getOnlineNames());
            return names;
        }
        if (args.length == 3 && "user".equalsIgnoreCase(args[0])) {
            return List.of("reset", "set");
        }
        if (args.length == 4 && "user".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[2])) {
            return StatField.keys();
        }
        return Collections.emptyList();
    }
}
