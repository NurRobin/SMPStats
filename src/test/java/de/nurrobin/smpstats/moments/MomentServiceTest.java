package de.nurrobin.smpstats.moments;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MomentServiceTest {

    @Test
    void flushesMergedBlockBreaksOnShutdown() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition blockDef = new MomentDefinition("diamond", MomentDefinition.TriggerType.BLOCK_BREAK,
                "Title {count}", "Detail {count}", 5, false, Set.of(Material.DIAMOND_ORE), 0, 0, Set.of(), false, Set.of());
        Settings settings = settings(List.of(blockDef));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);

        try (var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(uuid)).thenReturn(player);

            service.onBlockBreak(player, loc, Material.DIAMOND_ORE);
            service.onBlockBreak(player, loc, Material.DIAMOND_ORE);
            service.shutdown(); // flushes window
        }

        ArgumentCaptor<MomentEntry> captor = ArgumentCaptor.forClass(MomentEntry.class);
        verify(storage).saveMoment(captor.capture());
        MomentEntry entry = captor.getValue();
        assertEquals("diamond", entry.getType());
        assertTrue(entry.getDetail().contains("2"));
        assertTrue(entry.getPayload().contains("count"));
    }

    @Test
    void handlesFirstDeathAndLowHealthMoments() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.hasMoment(any(), any())).thenReturn(false).thenReturn(true); // only first death allowed

        MomentDefinition firstDeath = new MomentDefinition("first", MomentDefinition.TriggerType.FIRST_DEATH,
                "First", "First", 0, true, Set.of(), 0, 0, Set.of(), false, Set.of());
        MomentDefinition lowHp = new MomentDefinition("lowhp", MomentDefinition.TriggerType.DAMAGE_LOW_HP,
                "Low", "HP {health}", 0, false, Set.of(), 0, 2.0, Set.of("LAVA"), false, Set.of());
        Settings settings = settings(List.of(firstDeath, lowHp));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        when(player.getHealth()).thenReturn(3.0);
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);

        service.onDeath(player, loc, 0, "LAVA", false);
        service.onDeath(player, loc, 0, "LAVA", false); // second call should be ignored by first_only
        ArgumentCaptor<MomentEntry> deaths = ArgumentCaptor.forClass(MomentEntry.class);
        verify(storage, times(1)).saveMoment(deaths.capture());
        assertEquals("First", deaths.getValue().getTitle());

        service.onDamage(player, 1.5, "LAVA");
        ArgumentCaptor<MomentEntry> moments = ArgumentCaptor.forClass(MomentEntry.class);
        verify(storage, times(2)).saveMoment(moments.capture());
        assertTrue(moments.getAllValues().get(1).getDetail().contains("HP"));
    }

    private Settings settings(List<MomentDefinition> defs) {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(0),
                new SkillWeights.CombatWeights(0, 0, 0),
                new SkillWeights.ExplorationWeights(0, 0),
                new SkillWeights.BuilderWeights(0),
                new SkillWeights.FarmerWeights(0, 0)
        );
        return new Settings(true, true, true, true, true, true, true,
                false, 0, "KEY", 1, weights,
                true, 0, 1, true, 1, defs, List.of(),
                false, 1, 1, false,
                true, true, 1, 1,
                false, 1, 0, 0, 0, 0,
                false, 1, 1, "", 1, 1);
    }
}
