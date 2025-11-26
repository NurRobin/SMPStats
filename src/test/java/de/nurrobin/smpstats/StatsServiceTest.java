package de.nurrobin.smpstats;

import de.nurrobin.smpstats.commands.StatField;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.skills.SkillWeights;
import de.nurrobin.smpstats.timeline.TimelineService;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsServiceTest {
    private static final SkillWeights DEFAULT_WEIGHTS = new SkillWeights(
            new SkillWeights.MiningWeights(0.1),
            new SkillWeights.CombatWeights(0.2, 0.3, 0.4),
            new SkillWeights.ExplorationWeights(0.5, 0.6),
            new SkillWeights.BuilderWeights(0.7),
            new SkillWeights.FarmerWeights(0.8, 0.9)
    );

    @Test
    void handleJoinAndQuitPersistsStats() throws Exception {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.loadOrCreate(uuid, "Alex")).thenReturn(record);

        StatsService service = new StatsService(pluginWith(Optional.empty()), storage, settings(true, true, true, true, true, true));

        Player player = mockPlayer(uuid, "Alex");
        service.handleJoin(player);
        Thread.sleep(2);
        service.handleQuit(player);

        ArgumentCaptor<StatsRecord> saved = ArgumentCaptor.forClass(StatsRecord.class);
        verify(storage).save(saved.capture());
        StatsRecord persisted = saved.getValue();
        assertEquals("Alex", persisted.getName());
        assertTrue(persisted.getLastJoin() > 0);
        assertTrue(persisted.getPlaytimeMillis() >= 0);
    }

    @Test
    void flushOnlineSavesAndSnapshotsTimeline() throws Exception {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.loadOrCreate(uuid, "Alex")).thenReturn(record);

        TimelineService timeline = mock(TimelineService.class);
        StatsService service = new StatsService(pluginWith(Optional.of(timeline)), storage, settings(true, true, true, true, true, true));

        Player player = mockPlayer(uuid, "Alex");
        service.handleJoin(player);
        Thread.sleep(2);
        service.flushOnline();

        verify(storage, atLeastOnce()).save(any(StatsRecord.class));
        verify(timeline).snapshot(any(StatsRecord.class));
    }

    @Test
    void addersIncrementWhenEnabled() throws Exception {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.loadOrCreate(uuid, "Alex")).thenReturn(record);

        StatsService service = new StatsService(pluginWith(Optional.empty()), storage, settings(true, true, true, true, true, true));
        service.handleJoin(mockPlayer(uuid, "Alex"));

        service.addPlayerKill(uuid);
        service.addMobKill(uuid);
        service.addDistance(uuid, World.Environment.NORMAL, 12.5);
        service.addDistance(uuid, World.Environment.NETHER, 3.0);
        service.addDistance(uuid, World.Environment.THE_END, 1.5);
        service.addBiome(uuid, "desert");
        service.addDamageDealt(uuid, 2.5);
        service.addDamageTaken(uuid, 3.5);
        service.addCrafted(uuid, 4);
        service.addConsumed(uuid);

        StatsRecord snapshot = service.getStats(uuid).orElseThrow();
        assertEquals(1, snapshot.getPlayerKills());
        assertEquals(1, snapshot.getMobKills());
        assertEquals(12.5, snapshot.getDistanceOverworld());
        assertEquals(3.0, snapshot.getDistanceNether());
        assertEquals(1.5, snapshot.getDistanceEnd());
        assertTrue(snapshot.getBiomesVisited().contains("desert"));
        assertEquals(2.5, snapshot.getDamageDealt());
        assertEquals(3.5, snapshot.getDamageTaken());
        assertEquals(4, snapshot.getItemsCrafted());
        assertEquals(1, snapshot.getItemsConsumed());
    }

    @Test
    void addersAreIgnoredWhenDisabled() throws Exception {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        StatsStorage storage = mock(StatsStorage.class);
        when(storage.loadOrCreate(uuid, "Alex")).thenReturn(record);

        StatsService service = new StatsService(pluginWith(Optional.empty()), storage, settings(false, false, false, false, false, false));
        service.handleJoin(mockPlayer(uuid, "Alex"));

        service.addPlayerKill(uuid);
        service.addMobKill(uuid);
        service.addDistance(uuid, World.Environment.NORMAL, 5.0);
        service.addBiome(uuid, "savanna");
        service.addDamageDealt(uuid, 1.0);
        service.addDamageTaken(uuid, 2.0);
        service.addCrafted(uuid, 2);
        service.addConsumed(uuid);

        StatsRecord snapshot = service.getStats(uuid).orElseThrow();
        assertEquals(0, snapshot.getPlayerKills());
        assertEquals(0, snapshot.getMobKills());
        assertEquals(0.0, snapshot.getDistanceOverworld());
        assertTrue(snapshot.getBiomesVisited().isEmpty());
        assertEquals(0.0, snapshot.getDamageDealt());
        assertEquals(0.0, snapshot.getDamageTaken());
        assertEquals(0, snapshot.getItemsCrafted());
        assertEquals(0, snapshot.getItemsConsumed());
    }

    @Test
    void resetAndSetStatUseStoredRecord() throws Exception {
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        record.setPlaytimeMillis(5);
        record.setDeaths(2);
        record.setLastDeathCause("FIRE");
        record.setPlayerKills(3);
        record.setMobKills(4);
        record.setBlocksPlaced(5);
        record.setBlocksBroken(6);
        record.setDistanceOverworld(7);
        record.setDistanceNether(8);
        record.setDistanceEnd(9);
        record.setDamageDealt(10);
        record.setDamageTaken(11);
        record.setItemsCrafted(12);
        record.setItemsConsumed(13);
        record.setBiomesVisited(new LinkedHashSet<>(List.of("plains", "desert")));

        StatsStorage storage = mock(StatsStorage.class);
        when(storage.load(uuid)).thenReturn(Optional.of(record));

        StatsService service = new StatsService(pluginWith(Optional.empty()), storage, settings(true, true, true, true, true, true));

        assertTrue(service.resetStats(uuid));
        assertEquals(0, record.getPlaytimeMillis());
        assertEquals(0, record.getDeaths());
        assertNull(record.getLastDeathCause());
        assertEquals(0, record.getPlayerKills());
        assertEquals(0, record.getMobKills());
        assertEquals(0, record.getBlocksPlaced());
        assertEquals(0, record.getBlocksBroken());
        assertEquals(0, record.getDistanceOverworld());
        assertEquals(0, record.getDistanceNether());
        assertEquals(0, record.getDistanceEnd());
        assertEquals(0, record.getDamageDealt());
        assertEquals(0, record.getDamageTaken());
        assertEquals(0, record.getItemsCrafted());
        assertEquals(0, record.getItemsConsumed());
        assertTrue(record.getBiomesVisited().isEmpty());
        verify(storage).save(record);

        assertTrue(service.setStat(uuid, StatField.DIST_END, 42));
        assertEquals(42, record.getDistanceEnd());
        verify(storage, times(2)).save(record);

        UUID missing = UUID.randomUUID();
        when(storage.load(missing)).thenReturn(Optional.empty());
        assertFalse(service.setStat(missing, StatField.DEATHS, 1));
        assertFalse(service.resetStats(missing));
    }

    private SMPStats pluginWith(Optional<TimelineService> timeline) {
        SMPStats plugin = mock(SMPStats.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getTimelineService()).thenReturn(timeline);
        return plugin;
    }

    private Settings settings(boolean trackMovement,
                              boolean trackKills,
                              boolean trackBiomes,
                              boolean trackCrafting,
                              boolean trackDamage,
                              boolean trackConsumption) {
        Settings settings = mock(Settings.class);
        when(settings.getSkillWeights()).thenReturn(DEFAULT_WEIGHTS);
        when(settings.isTrackMovement()).thenReturn(trackMovement);
        when(settings.isTrackKills()).thenReturn(trackKills);
        when(settings.isTrackBiomes()).thenReturn(trackBiomes);
        when(settings.isTrackCrafting()).thenReturn(trackCrafting);
        when(settings.isTrackDamage()).thenReturn(trackDamage);
        when(settings.isTrackConsumption()).thenReturn(trackConsumption);
        return settings;
    }

    private Player mockPlayer(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        return player;
    }
}
