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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class StatsCommand implements CommandExecutor, TabCompleter {
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
            StatsFormatter.render(sender, plugin, statsService, target.get());
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Spieler '" + targetName + "' nicht gefunden.");
        return true;
    }

    private void showStats(CommandSender sender, UUID uuid, String displayName) {
        StatsFormatter.render(sender, plugin, statsService, uuid, displayName);
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
