package de.nurrobin.smpstats.commands;

import com.google.gson.Gson;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class StatsCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withLocale(Locale.GERMANY).withZone(ZoneId.systemDefault());
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.GERMANY);

    private final SMPStats plugin;
    private final StatsService statsService;
    private final Gson gson = new Gson();

    public StatsCommand(SMPStats plugin, StatsService statsService) {
        this.plugin = plugin;
        this.statsService = statsService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Bitte nutze /stats <player> in der Konsole.");
                return true;
            }
            showStats(sender, player.getUniqueId(), player.getName());
            return true;
        }

        String sub = args[0];
        if ("json".equalsIgnoreCase(sub)) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Nur Spieler können /stats json nutzen.");
                return true;
            }
            statsService.getStats(player.getUniqueId())
                    .ifPresentOrElse(
                            record -> sender.sendMessage(ChatColor.GRAY + gson.toJson(record)),
                            () -> sender.sendMessage(ChatColor.RED + "Keine Stats gefunden.")
                    );
            return true;
        }

        if ("dump".equalsIgnoreCase(sub)) {
            List<StatsRecord> all = statsService.getAllStats();
            for (StatsRecord record : all) {
                plugin.getLogger().info(record.getName() + " -> " + gson.toJson(record));
            }
            sender.sendMessage(ChatColor.GREEN + "Alle Stats wurden in die Konsole geschrieben.");
            return true;
        }

        // Otherwise try to find stats for the given player name
        String targetName = sub;
        Optional<StatsRecord> target = statsService.getStatsByName(targetName);
        if (target.isPresent()) {
            sendStats(sender, target.get());
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Spieler '" + targetName + "' nicht gefunden.");
        return true;
    }

    private void showStats(CommandSender sender, UUID uuid, String displayName) {
        Optional<StatsRecord> record = statsService.getStats(uuid);
        if (record.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Keine Statistiken für " + displayName + " gefunden.");
            return;
        }
        sendStats(sender, record.get());
    }

    private void sendStats(CommandSender sender, StatsRecord record) {
        sender.sendMessage(ChatColor.DARK_AQUA + "╔════════════ " + ChatColor.AQUA + "SMPStats" + ChatColor.DARK_AQUA + " ════════════");
        sender.sendMessage(ChatColor.GRAY + "  Spieler: " + ChatColor.WHITE + record.getName());
        sender.sendMessage(formatLine("Spielzeit", formatDuration(record.getPlaytimeMillis())));
        sender.sendMessage(formatLine("Erster Join", formatTimestamp(record.getFirstJoin())));
        sender.sendMessage(formatLine("Letzter Join", formatTimestamp(record.getLastJoin())));
        sender.sendMessage(formatLine("Tode", NUMBER.format(record.getDeaths()) + lastDeathSuffix(record)));
        sender.sendMessage(formatLine("Kills", "PvP " + NUMBER.format(record.getPlayerKills()) + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Mobs " + NUMBER.format(record.getMobKills())));
        sender.sendMessage(formatLine("Blöcke", "Platziert " + NUMBER.format(record.getBlocksPlaced()) + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Abgebaut " + NUMBER.format(record.getBlocksBroken())));
        sender.sendMessage(formatLine("Distanz", String.format(Locale.GERMANY, "OW %.1fm" + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Nether %.1fm" + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "End %.1fm",
                record.getDistanceOverworld(), record.getDistanceNether(), record.getDistanceEnd())));
        sender.sendMessage(formatLine("Damage", String.format(Locale.GERMANY, "Dealt %.1f" + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Taken %.1f",
                record.getDamageDealt(), record.getDamageTaken())));
        sender.sendMessage(formatLine("Crafting", "Items gefertigt " + NUMBER.format(record.getItemsCrafted())));
        sender.sendMessage(formatLine("Verzehrt", "Items konsumiert " + NUMBER.format(record.getItemsConsumed())));
        sender.sendMessage(formatLine("Biome", NUMBER.format(record.getBiomesVisited().size())));
        sender.sendMessage(ChatColor.DARK_AQUA + "╚═════════════════════════════════");
    }

    private String lastDeathSuffix(StatsRecord record) {
        return record.getLastDeathCause() != null
                ? ChatColor.DARK_GRAY + " (letzte: " + ChatColor.WHITE + record.getLastDeathCause() + ChatColor.DARK_GRAY + ")"
                : "";
    }

    private String formatLine(String title, String value) {
        return ChatColor.GRAY + "  • " + ChatColor.AQUA + title + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + value;
    }

    private String formatTimestamp(long epochMillis) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("json");
            suggestions.add("dump");
            suggestions.addAll(statsService.getOnlineNames());
            return suggestions;
        }
        return Collections.emptyList();
    }
}
