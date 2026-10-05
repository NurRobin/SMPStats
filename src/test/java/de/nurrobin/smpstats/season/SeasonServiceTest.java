package de.nurrobin.smpstats.season;

import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeasonServiceTest {

    @TempDir
    Path tempDir;

    private static final UUID BEA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private SeasonStore store;
    private Path root;

    private SeasonService newService(Executor executor) throws Exception {
        Path dataDir = Files.createDirectory(tempDir.resolve("data"));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(plugin.getServer()).thenReturn(server);
        StatsStorage storage = new StatsStorage(plugin);
        storage.init();
        store = new SeasonStore(storage);
        root = Files.createDirectory(dataDir.resolve("seasons"));
        return new SeasonService(Logger.getLogger("test"), root, store, new SeasonImporter(), executor);
    }

    private void writeSeason(String season) throws Exception {
        Path stats = Files.createDirectories(root.resolve(season).resolve("world/stats"));
        Files.writeString(stats.resolve(BEA + ".json"), "{\"stats\":{\"minecraft:custom\":{\"minecraft:jump\":7}}}");
    }

    @Test
    void importsSeasonAndReportsOutcome() throws Exception {
        SeasonService service = newService(Runnable::run);
        writeSeason("2025");

        SeasonService.Outcome o = service.importSeason("2025").get();

        assertEquals(1, o.players());
        assertEquals(1, o.statRows());
        assertTrue(service.meta("2025").isPresent());
        assertEquals(7.0, store.playerStats("2025", BEA).get("minecraft:custom/minecraft:jump"));
    }

    @Test
    void autoImportOnlyPicksMissingSeasons() throws Exception {
        SeasonService service = newService(Runnable::run);
        writeSeason("2025");
        writeSeason("2024");
        Files.createDirectories(root.resolve("../evil"));
        Files.createDirectories(root.resolve("mit leerzeichen"));
        service.importSeason("2024").get();

        assertEquals(List.of("2025"), service.pendingSeasons());
        service.autoImport();
        assertTrue(service.meta("2025").isPresent());
        assertTrue(service.pendingSeasons().isEmpty());
    }

    @Test
    void rejectsPathTraversalAndBadNames() throws Exception {
        SeasonService service = newService(Runnable::run);
        for (String bad : List.of("../data", "..", "a/b", "", "x".repeat(33), "2025 ")) {
            ExecutionException ex = assertThrows(ExecutionException.class, () -> service.importSeason(bad).get(), bad);
            assertInstanceOf(IllegalArgumentException.class, ex.getCause(), bad);
        }
    }

    @Test
    void emptySeasonFolderKeepsExistingSeason() throws Exception {
        SeasonService service = newService(Runnable::run);
        writeSeason("2025");
        service.importSeason("2025").get();
        long before = service.meta("2025").orElseThrow().importedAt();
        Files.delete(root.resolve("2025/world/stats").resolve(BEA + ".json"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> service.importSeason("2025").get());

        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals(before, service.meta("2025").orElseThrow().importedAt());
        assertEquals(1, store.statValues("2025", "minecraft:custom/minecraft:jump").size());
    }

    @Test
    void secondImportOfSameSeasonWhileRunningIsRejected() throws Exception {
        java.util.List<Runnable> queued = new java.util.ArrayList<>();
        SeasonService service = newService(queued::add);
        writeSeason("2025");

        var first = service.importSeason("2025");
        ExecutionException ex = assertThrows(ExecutionException.class, () -> service.importSeason("2025").get());
        assertInstanceOf(IllegalStateException.class, ex.getCause());

        queued.forEach(Runnable::run);
        assertEquals(1, first.get().players());
        assertDoesNotThrow(() -> service.importSeason("2025"), "lock is released after the run");
    }
}
