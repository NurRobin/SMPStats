package de.nurrobin.smpstats.commands;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.skills.SkillProfile;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsFormatterTest {

    @Test
    void sendsErrorWhenStatsMissing() {
        CommandSender sender = mock(CommandSender.class);
        StatsService statsService = mock(StatsService.class);
        UUID uuid = UUID.randomUUID();
        when(statsService.getStats(uuid)).thenReturn(Optional.empty());

        StatsFormatter.render(sender, mock(SMPStats.class), statsService, uuid, "Alex");

        verify(sender).sendMessage(contains("Keine Statistiken"));
    }

    @Test
    void rendersStatsAndSkillProfile() {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        record.setPlaytimeMillis(3_720_000); // 1h 2m
        record.setFirstJoin(1_000);
        record.setLastJoin(2_000);
        record.setDeaths(3);
        record.setLastDeathCause("Fall");
        record.setPlayerKills(4);
        record.setMobKills(5);
        record.setBlocksPlaced(6);
        record.setBlocksBroken(7);
        record.setDistanceOverworld(8.5);
        record.setDistanceNether(9.5);
        record.setDistanceEnd(10.5);
        record.setDamageDealt(11.5);
        record.setDamageTaken(12.5);
        record.setItemsCrafted(13);
        record.setItemsConsumed(14);
        record.setBiomesVisited(new LinkedHashSet<>(List.of("desert", "plains")));

        StatsService statsService = mock(StatsService.class);
        when(statsService.getStats(uuid)).thenReturn(Optional.of(record));
        when(statsService.getSkillProfile(uuid)).thenReturn(Optional.of(new SkillProfile(1.1, 2.2, 3.3, 4.4, 5.5)));

        CommandSender sender = mock(CommandSender.class);

        StatsFormatter.render(sender, mock(SMPStats.class), statsService, uuid, "Alex");

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeast(1)).sendMessage(messages.capture());
        List<String> lines = messages.getAllValues();

        assertTrue(lines.stream().anyMatch(line -> line.contains("Spielzeit") && line.contains("1h 2m")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Kills") && line.contains("PvP 4")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Bl\u00f6cke") && line.contains("6")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Distanz") && line.contains("8,5m")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Damage") && line.contains("12,5")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Biome") && line.contains("2")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Skills")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Total") && line.contains("16,5")));
    }
}
