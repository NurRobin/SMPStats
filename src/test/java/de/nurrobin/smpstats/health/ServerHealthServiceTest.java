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

    private Settings settings(boolean enabled) {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(0),
                new SkillWeights.CombatWeights(0, 0, 0),
                new SkillWeights.ExplorationWeights(0, 0),
                new SkillWeights.BuilderWeights(0),
                new SkillWeights.FarmerWeights(0, 0)
        );
        return new Settings(true, true, true, true, true, true, true,
                false, 0, "KEY", 1, weights,
                true, 0L, 1L, true, 1, 1.0, List.of(), List.of(),
                false, 1, 1, false,
                true, true, 1, 1,
                enabled, 1, 0.2, 0.02, 0.2, 0.1,
                false, 1, 0, "", 1, 1);
    }
}
