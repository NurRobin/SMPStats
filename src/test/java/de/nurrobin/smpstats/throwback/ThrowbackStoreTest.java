package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThrowbackStoreTest {
    @TempDir
    Path tempDir;

    private ThrowbackStore newStore() throws Exception {
        Path dataDir = Files.createDirectory(tempDir.resolve("data-" + UUID.randomUUID()));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(plugin.getServer()).thenReturn(server);
        StatsStorage storage = new StatsStorage(plugin);
        storage.init();
        return new ThrowbackStore(storage);
    }

    @Test
    void savesAndReadsBackProgressIncludingNulls() throws Exception {
        ThrowbackStore store = newStore();
        UUID id = UUID.randomUUID();

        assertTrue(store.find(id).isEmpty());
        store.save(id, Progress.fresh());
        assertEquals(Progress.fresh(), store.find(id).orElseThrow());

        store.save(id, new Progress(300L, 200L, "2026-1"));
        Progress read = store.find(id).orElseThrow();
        assertEquals(300L, read.completedAt());
        assertEquals(200L, read.rulesConfirmedAt());
        assertEquals("2026-1", read.rulesVersion());
        assertTrue(read.completed());
    }

    @Test
    void deleteRemovesTheRow() throws Exception {
        ThrowbackStore store = newStore();
        UUID id = UUID.randomUUID();
        store.save(id, Progress.fresh());

        store.delete(id);

        assertTrue(store.find(id).isEmpty());
    }

    @Test
    void cachedRepositoryWritesBehindInOrderAndSurvivesARestart() throws Exception {
        ThrowbackStore store = newStore();
        CachedProgressRepository repo = new CachedProgressRepository(store, Logger.getLogger("test"));
        UUID id = UUID.randomUUID();

        repo.save(id, Progress.fresh());
        repo.save(id, Progress.fresh().withRules(50L, "2026-1"));
        repo.save(id, Progress.fresh().withRules(50L, "2026-1").withCompleted(90L));
        assertEquals(90L, repo.find(id).orElseThrow().completedAt(), "cache answers immediately");
        repo.close();

        Progress persisted = store.find(id).orElseThrow();
        assertEquals(90L, persisted.completedAt());
        assertEquals(50L, persisted.rulesConfirmedAt());
    }

    @Test
    void preloadReadsExistingRowsOffTheMainThread() throws Exception {
        ThrowbackStore store = newStore();
        UUID id = UUID.randomUUID();
        store.save(id, new Progress(1L, 1L, "v"));
        CachedProgressRepository repo = new CachedProgressRepository(store, Logger.getLogger("test"));

        repo.preload(id);

        assertTrue(repo.find(id).orElseThrow().completed());
        repo.close();
    }
}
