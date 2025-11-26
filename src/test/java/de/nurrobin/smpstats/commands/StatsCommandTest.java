package de.nurrobin.smpstats.commands;

import com.google.gson.Gson;
import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StatsCommandTest {

    @Test
    void requiresPlayerForSelfStats() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService stats = mock(StatsService.class);
        StatsCommand command = new StatsCommand(plugin, stats);

        CommandSender console = mock(CommandSender.class);
        command.onCommand(console, mock(Command.class), "stats", new String[]{});

        verify(console).sendMessage(ChatColor.RED + "Bitte nutze /stats <player> in der Konsole.");
    }

    @Test
    void returnsJsonForPlayer() {
        SMPStats plugin = mock(SMPStats.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsService stats = mock(StatsService.class);
        StatsCommand command = new StatsCommand(plugin, stats);

        Player sender = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(sender.getUniqueId()).thenReturn(uuid);
        StatsRecord record = new StatsRecord(uuid, "Alex");
        when(stats.getStats(uuid)).thenReturn(Optional.of(record));

        command.onCommand(sender, mock(Command.class), "stats", new String[]{"json"});

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender).sendMessage(messages.capture());
        assertTrue(messages.getValue().contains("\"name\":\"Alex\""));
    }

    @Test
    void dumpsAllStatsToLog() {
        SMPStats plugin = mock(SMPStats.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsService stats = mock(StatsService.class);
        StatsCommand command = new StatsCommand(plugin, stats);

        when(stats.getAllStats()).thenReturn(List.of(new StatsRecord(UUID.randomUUID(), "Alex")));

        CommandSender sender = mock(CommandSender.class);
        command.onCommand(sender, mock(Command.class), "stats", new String[]{"dump"});

        verify(sender).sendMessage(ChatColor.GREEN + "Alle Stats wurden in die Konsole geschrieben.");
    }

    @Test
    void rendersTargetStatsAndHandlesMissing() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService stats = mock(StatsService.class);
        StatsCommand command = new StatsCommand(plugin, stats);

        StatsRecord record = new StatsRecord(UUID.randomUUID(), "Alex");
        when(stats.getStatsByName("Alex")).thenReturn(Optional.of(record));

        CommandSender sender = mock(CommandSender.class);
        try (MockedStatic<StatsFormatter> formatter = mockStatic(StatsFormatter.class)) {
            command.onCommand(sender, mock(Command.class), "stats", new String[]{"Alex"});
            formatter.verify(() -> StatsFormatter.render(sender, plugin, stats, record));
        }

        when(stats.getStatsByName("Missing")).thenReturn(Optional.empty());
        command.onCommand(sender, mock(Command.class), "stats", new String[]{"Missing"});
        verify(sender).sendMessage(ChatColor.RED + "Spieler 'Missing' nicht gefunden.");
    }

    @Test
    void tabCompleteAddsKeywordsAndNames() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService stats = mock(StatsService.class);
        when(stats.getOnlineNames()).thenReturn(List.of("Alex", "Bea"));
        StatsCommand command = new StatsCommand(plugin, stats);

        List<String> suggestions = command.onTabComplete(mock(CommandSender.class), mock(Command.class), "stats", new String[]{""});
        assertTrue(suggestions.contains("json"));
        assertTrue(suggestions.contains("dump"));
        assertTrue(suggestions.contains("Alex"));
    }
}
