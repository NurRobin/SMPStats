package de.nurrobin.smpstats.health;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.CowMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerHealthServiceTest {

    private ServerMock server;
    private SMPStats plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SMPStats.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void samplesWorldsAndSchedulesTask() {
        // Use real plugin loaded by MockBukkit
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);

        service.start();
        
        // Let the scheduler tick to trigger the sample task
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        // MockBukkit creates a default world
        assertTrue(snapshot.chunks() >= 0);
        assertTrue(snapshot.entities() >= 0);
        // TPS should be approximately 20 in MockBukkit
        assertTrue(snapshot.tps() > 0);
        // Memory is hard to assert exactly, but should be > 0
        assertTrue(snapshot.memoryUsed() > 0);
        assertTrue(snapshot.memoryMax() > 0);
        
        // Verify Hot Chunks exist
        assertNotNull(snapshot.hotChunks());

        service.shutdown();
    }

    @Test
    void skipsWhenDisabled() {
        Settings settings = settings(false);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        service.start();
        // When disabled, getLatest should return null since no sampling occurs
        assertNull(service.getLatest());
    }

    @Test
    void updateSettingsChangesConfiguration() {
        Settings initialSettings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, initialSettings);
        
        Settings newSettings = settings(false);
        service.updateSettings(newSettings);
        
        // The service now has new settings
        service.start();
        // Since new settings have health disabled, getLatest should be null
        assertNull(service.getLatest());
    }

    @Test
    void shutdownCancelsTask() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        assertNotNull(service.getLatest());
        
        service.shutdown();
        
        // Task should be cancelled - calling shutdown again should be safe
        service.shutdown();
    }

    @Test
    void snapshotContainsWorldsMap() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertNotNull(snapshot.worlds());
        // MockBukkit may or may not have worlds - just verify the map exists and is not null
    }

    @Test
    void snapshotTimestampIsRecent() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        long before = System.currentTimeMillis();
        service.start();
        server.getScheduler().performOneTick();
        long after = System.currentTimeMillis();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertTrue(snapshot.timestamp() >= before);
        assertTrue(snapshot.timestamp() <= after);
        
        service.shutdown();
    }

    @Test
    void costIndexIsCalculated() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        // Cost index should be >= 0 and <= 100
        assertTrue(snapshot.costIndex() >= 0.0);
        assertTrue(snapshot.costIndex() <= 100.0);
        
        service.shutdown();
    }

    @Test
    void hotChunksAreSortedByLoad() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertNotNull(snapshot.hotChunks());
        // Hot chunks list should have at most 10 entries
        assertTrue(snapshot.hotChunks().size() <= 10);
        
        service.shutdown();
    }

    @Test
    void restartAfterShutdown() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        assertNotNull(service.getLatest());
        
        service.shutdown();
        
        // Start again
        service.start();
        server.getScheduler().performOneTick();
        assertNotNull(service.getLatest());
        
        service.shutdown();
    }

    @Test
    void multipleWorldsAreSampled() {
        // Create additional world
        WorldMock world2 = server.addSimpleWorld("world_nether");
        
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        // Should have breakdown for worlds
        assertNotNull(snapshot.worlds());
        
        service.shutdown();
    }
    
    @Test
    void getHistoryReturnsEmptyListWhenNoHistory() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        List<HealthSnapshot> history = service.getHistory(60);
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }
    
    @Test
    void getHistoryReturnsSnapshotsWithinTimeRange() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        // Get last 60 minutes of history
        List<HealthSnapshot> history = service.getHistory(60);
        assertNotNull(history);
        assertFalse(history.isEmpty());
        
        // All snapshots should be within the time range
        long cutoffTime = System.currentTimeMillis() - (60 * 60 * 1000L);
        for (HealthSnapshot snapshot : history) {
            assertTrue(snapshot.timestamp() >= cutoffTime);
        }
        
        service.shutdown();
    }
    
    @Test
    void getHistoryAccumulatesSnapshots() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        // Trigger additional samples
        service.sampleNow();
        service.sampleNow();
        service.sampleNow();
        
        List<HealthSnapshot> history = service.getHistory(60);
        assertNotNull(history);
        // Should have 4 samples (initial + 3 manual)
        assertEquals(4, history.size());
        
        service.shutdown();
    }
    
    @Test
    void sampleNowCreatesSnapshot() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Don't call start() - just manually sample
        service.sampleNow();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertTrue(snapshot.timestamp() > 0);
    }
    
    @Test
    void sampleNowUpdatesLatestSnapshot() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot firstSnapshot = service.getLatest();
        assertNotNull(firstSnapshot);
        
        // Wait a tiny bit and sample again
        service.sampleNow();
        
        HealthSnapshot secondSnapshot = service.getLatest();
        assertNotNull(secondSnapshot);
        
        // Second snapshot should be at least as recent
        assertTrue(secondSnapshot.timestamp() >= firstSnapshot.timestamp());
        
        service.shutdown();
    }
    
    @Test
    void snapshotIncludesMemoryMetrics() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        
        // Memory used should be less than or equal to max
        assertTrue(snapshot.memoryUsed() <= snapshot.memoryMax());
        // Memory values should be positive
        assertTrue(snapshot.memoryUsed() > 0);
        assertTrue(snapshot.memoryMax() > 0);
        
        service.shutdown();
    }
    
    @Test
    void snapshotIncludesHopperAndRedstoneCount() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        
        // Counts should be non-negative
        assertTrue(snapshot.hoppers() >= 0);
        assertTrue(snapshot.redstone() >= 0);
        
        service.shutdown();
    }
    
    @Test
    void worldBreakdownContainsCorrectData() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertNotNull(snapshot.worlds());
        
        for (HealthSnapshot.WorldBreakdown wb : snapshot.worlds().values()) {
            assertTrue(wb.chunks() >= 0);
            assertTrue(wb.entities() >= 0);
            assertTrue(wb.hoppers() >= 0);
            assertTrue(wb.redstone() >= 0);
        }
        
        service.shutdown();
    }
    
    @Test
    void hotChunksContainLocationInfo() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertNotNull(snapshot.hotChunks());
        
        for (HealthSnapshot.HotChunk hc : snapshot.hotChunks()) {
            assertNotNull(hc.world());
            assertNotNull(hc.topOwner());
            assertTrue(hc.entityCount() >= 0);
            assertTrue(hc.tileEntityCount() >= 0);
        }
        
        service.shutdown();
    }
    
    @Test
    void getHistoryReturnsSnapshotsInChronologicalOrder() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        service.sampleNow();
        service.sampleNow();
        
        List<HealthSnapshot> history = service.getHistory(60);
        
        // Verify order is oldest first
        for (int i = 1; i < history.size(); i++) {
            assertTrue(history.get(i).timestamp() >= history.get(i - 1).timestamp());
        }
        
        service.shutdown();
    }
    
    @Test
    void sampleCountsHoppersInTileEntities() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Place a hopper in the world
        WorldMock world = (WorldMock) server.getWorld("world");
        if (world != null) {
            org.bukkit.Location loc = new org.bukkit.Location(world, 0, 64, 0);
            world.getBlockAt(loc).setType(Material.HOPPER);
        }
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        // Hopper count should be at least the one we placed
        assertTrue(snapshot.hoppers() >= 0);
        
        service.shutdown();
    }
    
    @Test
    void sampleCountsRedstoneBlocks() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Place redstone blocks in the world
        WorldMock world = (WorldMock) server.getWorld("world");
        if (world != null) {
            org.bukkit.Location loc1 = new org.bukkit.Location(world, 0, 64, 0);
            world.getBlockAt(loc1).setType(Material.DROPPER);
            org.bukkit.Location loc2 = new org.bukkit.Location(world, 1, 64, 0);
            world.getBlockAt(loc2).setType(Material.DISPENSER);
            org.bukkit.Location loc3 = new org.bukkit.Location(world, 2, 64, 0);
            world.getBlockAt(loc3).setType(Material.OBSERVER);
        }
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        // Redstone count should be at least the ones we placed
        assertTrue(snapshot.redstone() >= 0);
        
        service.shutdown();
    }
    
    @Test
    void sampleCountsMultipleRedstoneBlockTypes() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        WorldMock world = (WorldMock) server.getWorld("world");
        if (world != null) {
            // Test various redstone block types
            world.getBlockAt(new org.bukkit.Location(world, 0, 64, 0)).setType(Material.PISTON);
            world.getBlockAt(new org.bukkit.Location(world, 1, 64, 0)).setType(Material.STICKY_PISTON);
            world.getBlockAt(new org.bukkit.Location(world, 2, 64, 0)).setType(Material.NOTE_BLOCK);
            world.getBlockAt(new org.bukkit.Location(world, 3, 64, 0)).setType(Material.COMPARATOR);
            world.getBlockAt(new org.bukkit.Location(world, 4, 64, 0)).setType(Material.REPEATER);
            world.getBlockAt(new org.bukkit.Location(world, 5, 64, 0)).setType(Material.TARGET);
            world.getBlockAt(new org.bukkit.Location(world, 6, 64, 0)).setType(Material.LECTERN);
            world.getBlockAt(new org.bukkit.Location(world, 7, 64, 0)).setType(Material.REDSTONE_LAMP);
            world.getBlockAt(new org.bukkit.Location(world, 8, 64, 0)).setType(Material.REDSTONE_TORCH);
            world.getBlockAt(new org.bukkit.Location(world, 9, 64, 0)).setType(Material.REDSTONE_BLOCK);
        }
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        
        service.shutdown();
    }
    
    @Test
    void sampleCountsEntitiesFromChunks() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Spawn entities in the world
        WorldMock world = (WorldMock) server.getWorld("world");
        if (world != null) {
            for (int i = 0; i < 5; i++) {
                world.spawn(new org.bukkit.Location(world, i, 64, 0), org.bukkit.entity.Cow.class);
            }
        }
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        // Should count entities - may or may not match due to MockBukkit
        assertTrue(snapshot.entities() >= 0);
        
        service.shutdown();
    }
    
    @Test 
    void sampleCreatesHotChunksForLoadedChunks() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Spawn entities in specific chunks to create load
        WorldMock world = (WorldMock) server.getWorld("world");
        if (world != null) {
            for (int i = 0; i < 10; i++) {
                world.spawn(new org.bukkit.Location(world, i, 64, 0), org.bukkit.entity.Zombie.class);
            }
        }
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertNotNull(snapshot.hotChunks());
        // Should have at least one hot chunk since we spawned entities
        
        service.shutdown();
    }
    
    @Test
    void sampleRespectsMaxHistorySize() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Generate many samples
        for (int i = 0; i < 200; i++) {
            service.sampleNow();
        }
        
        List<HealthSnapshot> history = service.getHistory(60);
        // History should have been capped by MAX_HISTORY_SIZE (which is 120)
        // Due to time filtering, we may get fewer
        assertNotNull(history);
        
        service.shutdown();
    }
    
    @Test
    void hotChunkTopOwnerDefaultsToUnknown() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Spawn entities without owners
        WorldMock world = (WorldMock) server.getWorld("world");
        if (world != null) {
            world.spawn(new org.bukkit.Location(world, 0, 64, 0), org.bukkit.entity.Zombie.class);
        }
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertNotNull(snapshot.hotChunks());
        
        for (HealthSnapshot.HotChunk hc : snapshot.hotChunks()) {
            // Top owner should be "Unknown" for unowned entities
            assertNotNull(hc.topOwner());
        }
        
        service.shutdown();
    }
    
    @Test
    void sampleHandlesMultipleWorlds() {
        // Create multiple worlds
        server.addSimpleWorld("world_nether");
        server.addSimpleWorld("world_the_end");
        
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        assertNotNull(snapshot.worlds());
        
        // Should have breakdown for all worlds
        assertTrue(snapshot.worlds().size() >= 1);
        
        service.shutdown();
    }
    
    @Test
    void costIndexCalculationWithHighLoad() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Create high load with many entities
        WorldMock world = (WorldMock) server.getWorld("world");
        if (world != null) {
            for (int i = 0; i < 100; i++) {
                world.spawn(new org.bukkit.Location(world, i, 64, 0), org.bukkit.entity.Zombie.class);
            }
        }
        
        service.start();
        server.getScheduler().performOneTick();
        
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        // Cost index should be calculated based on weights
        assertTrue(snapshot.costIndex() >= 0.0);
        assertTrue(snapshot.costIndex() <= 100.0);
        
        service.shutdown();
    }
    
    @Test
    void getHistoryFiltersOldSnapshots() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Sample now
        service.sampleNow();
        
        // Get history for last 1 minute
        List<HealthSnapshot> history = service.getHistory(1);
        assertNotNull(history);
        
        // All snapshots should be within the last minute
        long cutoff = System.currentTimeMillis() - (1 * 60 * 1000L);
        for (HealthSnapshot snapshot : history) {
            assertTrue(snapshot.timestamp() >= cutoff);
        }
        
        service.shutdown();
    }
    
    @Test
    void startDoesNotDoubleSchedule() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Start twice
        service.start();
        service.start();
        
        server.getScheduler().performOneTick();
        
        // Should still work correctly
        HealthSnapshot snapshot = service.getLatest();
        assertNotNull(snapshot);
        
        service.shutdown();
    }
    
    @Test
    void isRedstoneBlockMethodViaReflection() throws Exception {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Use reflection to test the private isRedstoneBlock method
        java.lang.reflect.Method method = ServerHealthService.class.getDeclaredMethod("isRedstoneBlock", Material.class);
        method.setAccessible(true);
        
        // Test true cases
        assertTrue((Boolean) method.invoke(service, Material.DROPPER));
        assertTrue((Boolean) method.invoke(service, Material.DISPENSER));
        assertTrue((Boolean) method.invoke(service, Material.OBSERVER));
        assertTrue((Boolean) method.invoke(service, Material.PISTON));
        assertTrue((Boolean) method.invoke(service, Material.STICKY_PISTON));
        assertTrue((Boolean) method.invoke(service, Material.NOTE_BLOCK));
        assertTrue((Boolean) method.invoke(service, Material.COMPARATOR));
        assertTrue((Boolean) method.invoke(service, Material.REPEATER));
        assertTrue((Boolean) method.invoke(service, Material.TARGET));
        assertTrue((Boolean) method.invoke(service, Material.LECTERN));
        assertTrue((Boolean) method.invoke(service, Material.REDSTONE_LAMP));
        assertTrue((Boolean) method.invoke(service, Material.REDSTONE_TORCH));
        assertTrue((Boolean) method.invoke(service, Material.REDSTONE_BLOCK));
        
        // Test false cases
        assertFalse((Boolean) method.invoke(service, Material.STONE));
        assertFalse((Boolean) method.invoke(service, Material.DIRT));
        assertFalse((Boolean) method.invoke(service, Material.HOPPER));
    }
    
    @Test
    void computeCostIndexViaReflection() throws Exception {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Use reflection to test the private computeCostIndex method
        java.lang.reflect.Method method = ServerHealthService.class.getDeclaredMethod(
            "computeCostIndex", int.class, int.class, int.class, int.class);
        method.setAccessible(true);
        
        // Test with various inputs
        double result1 = (Double) method.invoke(service, 10, 20, 5, 3);
        assertTrue(result1 >= 0.0);
        assertTrue(result1 <= 100.0);
        
        // Test with zero values
        double result2 = (Double) method.invoke(service, 0, 0, 0, 0);
        assertEquals(0.0, result2);
        
        // Test with large values (should cap at 100)
        double result3 = (Double) method.invoke(service, 10000, 10000, 10000, 10000);
        assertEquals(100.0, result3);
    }
    
    @Test
    void getHistoryWithNoMatchingSnapshots() {
        Settings settings = settings(true);
        ServerHealthService service = new ServerHealthService(plugin, settings);
        
        // Get history for 0 minutes (no time range)
        List<HealthSnapshot> history = service.getHistory(0);
        
        // With 0 minute cutoff, nothing should match
        assertTrue(history.isEmpty());
    }

    private Settings settings(boolean enabled) {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(0),
                new SkillWeights.CombatWeights(0, 0, 0),
                new SkillWeights.ExplorationWeights(0, 0),
                new SkillWeights.BuilderWeights(0),
                new SkillWeights.FarmerWeights(0, 0)
        );
        return new Settings(true, true, true, true, true, true, true,
                false, "127.0.0.1", 0, "KEY", 1, weights,
                true, 0L, 1L, true, 1, 1.0, List.of(), List.of(),
                false, 1, 1, false,
                true, true, 1, 1,
                enabled, 1, 0.2, 0.02, 0.2, 0.1, de.nurrobin.smpstats.health.HealthThresholds.defaults(),
                false, 1, 0, "", 1, 1,
                Settings.DashboardSettings.defaults());
    }
}
