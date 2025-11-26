package de.nurrobin.smpstats.health;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.skills.SkillWeights;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServerHealthServiceTest {

    @Test
    void samplesWorldsAndSchedulesTask() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        World overworld = mock(World.class);
        when(overworld.getName()).thenReturn("world");
        org.bukkit.entity.Entity entityA = mock(org.bukkit.entity.Entity.class);
        org.bukkit.entity.Entity entityB = mock(org.bukkit.entity.Entity.class);
        org.bukkit.entity.Entity entityC = mock(org.bukkit.entity.Entity.class);
        when(overworld.getEntities()).thenReturn(List.of(entityA, entityB, entityC));
        Chunk overworldChunk = mockChunk(Material.HOPPER, Material.REDSTONE_BLOCK);
        when(overworld.getLoadedChunks()).thenReturn(new Chunk[]{overworldChunk});

        World nether = mock(World.class);
        when(nether.getName()).thenReturn("nether");
        org.bukkit.entity.Entity netherEntity = mock(org.bukkit.entity.Entity.class);
        when(nether.getEntities()).thenReturn(List.of(netherEntity));
        Chunk netherChunk = mockChunk(Material.DROPPER);
        when(nether.getLoadedChunks()).thenReturn(new Chunk[]{netherChunk});

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(5);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    scheduled.set(invocation.getArgument(1));
                    invocation.<Runnable>getArgument(1).run();
                    return task;
                });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(overworld, nether));

            Settings settings = settings(true);
            ServerHealthService service = new ServerHealthService(plugin, settings);

            service.start();
            HealthSnapshot snapshot = service.getLatest();
            assertNotNull(snapshot);
            assertEquals(2, snapshot.chunks());
            assertEquals(4, snapshot.entities());
            assertEquals(1, snapshot.hoppers());
            assertEquals(2, snapshot.redstone());

            service.shutdown();
            verify(scheduler).cancelTask(5);
        }

        assertNotNull(scheduled.get());
    }

    @Test
    void skipsWhenDisabled() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            Settings settings = settings(false);
            ServerHealthService service = new ServerHealthService(plugin, settings);
            service.start();
            verifyNoInteractions(scheduler);
        }
    }

    private Chunk mockChunk(Material... types) {
        Chunk chunk = mock(Chunk.class);
        BlockState[] states = new BlockState[types.length];
        for (int i = 0; i < types.length; i++) {
            BlockState state = mock(BlockState.class);
            when(state.getType()).thenReturn(types[i]);
            states[i] = state;
        }
        when(chunk.getTileEntities()).thenReturn(states);
        return chunk;
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
                true, 0, 1, true, 1, List.of(), List.of(),
                false, 1, 1, false,
                true, true, 1, 1,
                enabled, 1, 0.2, 0.02, 0.2, 0.1,
                false, 1, 0, "", 1, 1);
    }
}
