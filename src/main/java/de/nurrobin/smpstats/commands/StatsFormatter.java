package de.nurrobin.smpstats.commands;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.skills.SkillProfile;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public class StatsFormatter {
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withLocale(Locale.GERMANY).withZone(ZoneId.systemDefault());
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.GERMANY);

    public static void render(CommandSender sender, SMPStats plugin, StatsService statsService, UUID uuid, String displayName) {
        statsService.getStats(uuid).ifPresentOrElse(
                record -> render(sender, plugin, statsService, record),
                () -> sender.sendMessage(ChatColor.RED + "Keine Statistiken für " + displayName + " gefunden.")
        );
    }

    public static void render(CommandSender sender, SMPStats plugin, StatsService statsService, StatsRecord record) {
        sender.sendMessage(ChatColor.DARK_AQUA + "╔════════════ " + ChatColor.AQUA + "SMPStats" + ChatColor.DARK_AQUA + " ════════════");
        sender.sendMessage(ChatColor.GRAY + "  Spieler: " + ChatColor.WHITE + record.getName());
        sender.sendMessage(line("Spielzeit", formatDuration(record.getPlaytimeMillis())));
        sender.sendMessage(line("Erster Join", formatTimestamp(record.getFirstJoin())));
        sender.sendMessage(line("Letzter Join", formatTimestamp(record.getLastJoin())));
        sender.sendMessage(line("Tode", NUMBER.format(record.getDeaths()) + lastDeathSuffix(record)));
        sender.sendMessage(line("Kills", "PvP " + NUMBER.format(record.getPlayerKills()) + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Mobs " + NUMBER.format(record.getMobKills())));
        sender.sendMessage(line("Blöcke", "Platziert " + NUMBER.format(record.getBlocksPlaced()) + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Abgebaut " + NUMBER.format(record.getBlocksBroken())));
        sender.sendMessage(line("Distanz", String.format(Locale.GERMANY, "OW %.1fm" + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Nether %.1fm" + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "End %.1fm",
                record.getDistanceOverworld(), record.getDistanceNether(), record.getDistanceEnd())));
        sender.sendMessage(line("Damage", String.format(Locale.GERMANY, "Dealt %.1f" + ChatColor.DARK_GRAY + " | " + ChatColor.WHITE + "Taken %.1f",
                record.getDamageDealt(), record.getDamageTaken())));
        sender.sendMessage(line("Crafting", "Items gefertigt " + NUMBER.format(record.getItemsCrafted())));
        sender.sendMessage(line("Verzehrt", "Items konsumiert " + NUMBER.format(record.getItemsConsumed())));
        sender.sendMessage(line("Biome", NUMBER.format(record.getBiomesVisited().size())));
        statsService.getSkillProfile(record.getUuid()).ifPresent(profile -> {
            sender.sendMessage(ChatColor.DARK_AQUA + "  Skills:");
            sender.sendMessage(line("Mining", formatSkill(profile.mining())));
            sender.sendMessage(line("Combat", formatSkill(profile.combat())));
            sender.sendMessage(line("Exploration", formatSkill(profile.exploration())));
            sender.sendMessage(line("Builder", formatSkill(profile.builder())));
            sender.sendMessage(line("Farmer", formatSkill(profile.farmer())));
            sender.sendMessage(line("Total", formatSkill(profile.total())));
        });
        sender.sendMessage(ChatColor.DARK_AQUA + "╚═════════════════════════════════");
    }

    private static String line(String title, String value) {
        return ChatColor.GRAY + "  • " + ChatColor.AQUA + title + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + value;
    }

    private static String lastDeathSuffix(StatsRecord record) {
        return record.getLastDeathCause() != null
                ? ChatColor.DARK_GRAY + " (letzte: " + ChatColor.WHITE + record.getLastDeathCause() + ChatColor.DARK_GRAY + ")"
                : "";
    }

    private static String formatTimestamp(long epochMillis) {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    private static String formatSkill(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }
}
