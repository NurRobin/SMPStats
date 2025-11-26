package de.nurrobin.smpstats.health;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

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
