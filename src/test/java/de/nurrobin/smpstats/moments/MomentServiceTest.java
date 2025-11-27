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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
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

    @Test
    void flushesStaleWindowViaScheduler() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition gain = new MomentDefinition("gain", MomentDefinition.TriggerType.ITEM_GAIN,
                "Gained", "Items {count}", 0, false, Set.of(Material.DIAMOND), 0, 0, Set.of(), false, Set.of());
        Settings settings = settings(List.of(gain));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        Location loc = mock(Location.class);

        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);
        when(task.getTaskId()).thenReturn(7);

        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        try (var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(uuid)).thenReturn(player);
            when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                    .thenAnswer(invocation -> {
                        scheduled.set(invocation.getArgument(1));
                        return task;
                    });

            service.start();
            service.onItemGain(player, Material.DIAMOND, loc);

            scheduled.get().run(); // flushes because mergeSeconds = 0
            service.shutdown();
        }

        ArgumentCaptor<MomentEntry> captor = ArgumentCaptor.forClass(MomentEntry.class);
        verify(storage).saveMoment(captor.capture());
        MomentEntry entry = captor.getValue();
        assertTrue(entry.getDetail().contains("2") || entry.getDetail().contains("1"));
    }

    @Test
    void handlesMultipleDeathTriggersAndErrorsGracefully() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition fall = new MomentDefinition("fall", MomentDefinition.TriggerType.DEATH_FALL,
                "Fall {fall}", "Ouch", 0, false, Set.of(), 5, 0, Set.of("FALL"), false, Set.of());
        MomentDefinition explosion = new MomentDefinition("boom", MomentDefinition.TriggerType.DEATH_EXPLOSION,
                "Boom", "Exploded", 0, false, Set.of(), 0, 0, Set.of("CREEPER"), true, Set.of());
        MomentDefinition death = new MomentDefinition("death", MomentDefinition.TriggerType.DEATH,
                "Death", "Fire", 0, false, Set.of(), 0, 0, Set.of("FIRE"), false, Set.of());
        Settings settings = settings(List.of(fall, explosion, death));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        Location loc = mock(Location.class);

        service.onDeath(player, loc, 10, "FALL", false);
        service.onDeath(player, loc, 0, "CREEPER", true);
        service.onDeath(player, loc, 0, "FIRE", false);

        when(storage.loadMomentsSince(anyLong(), anyInt())).thenThrow(new java.sql.SQLException("fail"));
        when(storage.queryMoments(any(), anyString(), anyLong(), anyInt())).thenThrow(new java.sql.SQLException("fail"));

        ArgumentCaptor<MomentEntry> captor = ArgumentCaptor.forClass(MomentEntry.class);
        verify(storage, times(3)).saveMoment(captor.capture());
        assertEquals("Fall 10.0", captor.getAllValues().get(0).getTitle());
        assertTrue(service.getMomentsSince(0, 5).isEmpty());
        assertTrue(service.queryMoments(UUID.randomUUID(), "any", 0, 1).isEmpty());
    }
    
    @Test
    void updateSettingsChangesDefinitions() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        Settings initial = settings(List.of());
        MomentService service = new MomentService(plugin, storage, initial);
        
        MomentDefinition newDef = new MomentDefinition("new", MomentDefinition.TriggerType.BLOCK_BREAK,
                "New", "Details", 0, false, Set.of(Material.DIAMOND_ORE), 0, 0, Set.of(), false, Set.of());
        Settings updated = settings(List.of(newDef));
        
        // Just verify updateSettings doesn't throw
        service.updateSettings(updated);
    }
    
    @Test
    void onBossKillEmitsMoment() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition bossKill = new MomentDefinition("boss", MomentDefinition.TriggerType.BOSS_KILL,
                "Boss Killed", "Boss down", 0, false, Set.of(), 0, 0, Set.of(), false, Set.of("ENDER_DRAGON"));
        Settings settings = settings(List.of(bossKill));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        Location loc = mock(Location.class);
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(loc.getWorld()).thenReturn(world);
        when(world.getName()).thenReturn("world");
        when(loc.getBlockX()).thenReturn(0);
        when(loc.getBlockY()).thenReturn(64);
        when(loc.getBlockZ()).thenReturn(0);

        service.onBossKill(player, "ENDER_DRAGON", loc);

        ArgumentCaptor<MomentEntry> captor = ArgumentCaptor.forClass(MomentEntry.class);
        verify(storage).saveMoment(captor.capture());
        assertEquals("Boss Killed", captor.getValue().getTitle());
    }
    
    @Test
    void onItemGainHandlesMultipleMaterials() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition gain = new MomentDefinition("gain", MomentDefinition.TriggerType.ITEM_GAIN,
                "Gained", "Items", 5, false, Set.of(Material.DIAMOND, Material.EMERALD), 0, 0, Set.of(), false, Set.of());
        Settings settings = settings(List.of(gain));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("Alex");
        Location loc = mock(Location.class);

        try (var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(uuid)).thenReturn(player);
            
            service.onItemGain(player, Material.DIAMOND, loc);
            service.onItemGain(player, Material.EMERALD, loc);
            service.shutdown(); // flushes window
        }

        ArgumentCaptor<MomentEntry> captor = ArgumentCaptor.forClass(MomentEntry.class);
        verify(storage).saveMoment(captor.capture());
    }
    
    @Test
    void getRecentMomentsHandlesException() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.loadRecentMoments(anyInt())).thenThrow(new java.sql.SQLException("fail"));

        Settings settings = settings(List.of());
        MomentService service = new MomentService(plugin, storage, settings);

        List<MomentEntry> result = service.getRecentMoments(10);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void getRecentMomentsReturnsResults() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        
        MomentEntry entry = new MomentEntry(1L, UUID.randomUUID(), "test", 
                "Title", "Detail", "{}", "world", 0, 64, 0, 
                System.currentTimeMillis(), System.currentTimeMillis());
        when(storage.loadRecentMoments(10)).thenReturn(List.of(entry));

        Settings settings = settings(List.of());
        MomentService service = new MomentService(plugin, storage, settings);

        List<MomentEntry> result = service.getRecentMoments(10);
        assertEquals(1, result.size());
        assertEquals("Title", result.get(0).getTitle());
    }
    
    @Test
    void skipsMomentsWhenDisabled() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition def = new MomentDefinition("block", MomentDefinition.TriggerType.BLOCK_BREAK,
                "Title", "Detail", 0, false, Set.of(Material.DIAMOND_ORE), 0, 0, Set.of(), false, Set.of());
        Settings settings = disabledSettings(List.of(def));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        Location loc = mock(Location.class);

        service.onBlockBreak(player, loc, Material.DIAMOND_ORE);
        service.onDeath(player, loc, 0, "FALL", false);
        service.onDamage(player, 5.0, "LAVA");
        service.onItemGain(player, Material.DIAMOND, loc);
        service.onBossKill(player, "ENDER_DRAGON", loc);

        verify(storage, never()).saveMoment(any());
    }
    
    @Test
    void startDoesNothingWhenDisabled() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        Settings settings = disabledSettings(List.of());
        MomentService service = new MomentService(plugin, storage, settings);

        try (var bukkit = org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            service.start();
            bukkit.verify(() -> org.bukkit.Bukkit.getScheduler(), never());
        }
    }
    
    @Test
    void onDeathDoesNotTriggerForNonMatchingCause() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition death = new MomentDefinition("death", MomentDefinition.TriggerType.DEATH,
                "Death", "Detail", 0, false, Set.of(), 0, 0, Set.of("LAVA"), false, Set.of());
        Settings settings = settings(List.of(death));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        Location loc = mock(Location.class);

        // FALL doesn't match LAVA cause
        service.onDeath(player, loc, 0, "FALL", false);

        verify(storage, never()).saveMoment(any());
    }
    
    @Test
    void onDamageDoesNotTriggerWhenHealthAboveThreshold() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);

        MomentDefinition lowHp = new MomentDefinition("lowhp", MomentDefinition.TriggerType.DAMAGE_LOW_HP,
                "Low", "HP", 0, false, Set.of(), 0, 2.0, Set.of(), false, Set.of());
        Settings settings = settings(List.of(lowHp));

        MomentService service = new MomentService(plugin, storage, settings);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Alex");
        when(player.getHealth()).thenReturn(20.0);
        Location loc = mock(Location.class);
        when(player.getLocation()).thenReturn(loc);

        // 1 damage to 20 health leaves 19, way above threshold of 2
        service.onDamage(player, 1.0, "LAVA");

        verify(storage, never()).saveMoment(any());
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
                false, "127.0.0.1", 0, "KEY", 1, weights,
                true, 0L, 1L, true, 1, 1.0, defs, List.of(),
                false, 1, 1, false,
                true, true, 1, 1,
                false, 1, 0, 0, 0, 0, de.nurrobin.smpstats.health.HealthThresholds.defaults(),
                false, 1, 1, "", 1, 1,
                Settings.DashboardSettings.defaults());
    }
    
    private Settings disabledSettings(List<MomentDefinition> defs) {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(0),
                new SkillWeights.CombatWeights(0, 0, 0),
                new SkillWeights.ExplorationWeights(0, 0),
                new SkillWeights.BuilderWeights(0),
                new SkillWeights.FarmerWeights(0, 0)
        );
        // isMomentsEnabled = false (first boolean param)
        return new Settings(true, true, true, true, true, true, true,
                false, "127.0.0.1", 0, "KEY", 1, weights,
                false, 0L, 1L, true, 1, 1.0, defs, List.of(),
                false, 1, 1, false,
                true, true, 1, 1,
                false, 1, 0, 0, 0, 0, de.nurrobin.smpstats.health.HealthThresholds.defaults(),
                false, 1, 1, "", 1, 1,
                Settings.DashboardSettings.defaults());
    }
}
