package de.nurrobin.smpstats.season;

import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeasonStoreTest {

    @TempDir
    Path tempDir;

    private final UUID alex = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private final UUID bea = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private SeasonStore newStore() throws Exception {
        Path dataDir = Files.createDirectory(tempDir.resolve("data-" + UUID.randomUUID()));
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(dataDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        Server server = mock(Server.class);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(plugin.getServer()).thenReturn(server);
        StatsStorage storage = new StatsStorage(plugin);
        storage.init();
        return new SeasonStore(storage);
    }

    private SeasonData sample(String season) {
        return new SeasonData(season, "stats.db,world/stats",
                Map.of(alex, new SeasonPlayer(alex, "Alex", 7_200_000L), bea, new SeasonPlayer(bea, "Bea", 60_000L)),
                Map.of(alex, Map.of("smpstats:blocks_broken", 500.0, "minecraft:custom/minecraft:jump", 0.0),
                        bea, Map.of("smpstats:blocks_broken", 20.0)),
                Map.of(alex, Map.of("minecraft:story/enter_the_nether", 1_000L)));
    }

    @Test
    void replaceStoresEverythingAndMeta() throws Exception {
        SeasonStore store = newStore();
        int rows = store.replace(sample("2025"), 42L);

        assertEquals(3, rows);
        SeasonMeta meta = store.meta("2025").orElseThrow();
        assertEquals(2, meta.players());
        assertEquals(3, meta.statRows());
        assertEquals(42L, meta.importedAt());
        assertEquals("stats.db,world/stats", meta.source());
        assertEquals(Map.of(alex, 500.0, bea, 20.0), store.statValues("2025", "smpstats:blocks_broken"));
        assertEquals(500.0, store.playerStats("2025", alex).get("smpstats:blocks_broken"));
        assertEquals(Map.of(alex, 1_000L), store.advancementTimes("2025", "minecraft:story/enter_the_nether"));
        assertEquals("Bea", store.player("2025", bea).orElseThrow().name());
        assertTrue(store.meta("2024").isEmpty());
    }

    @Test
    void statValuesSkipZero() throws Exception {
        SeasonStore store = newStore();
        store.replace(sample("2025"), 1L);
        assertTrue(store.statValues("2025", "minecraft:custom/minecraft:jump").isEmpty());
    }

    @Test
    void replaceTwiceDoesNotDuplicate() throws Exception {
        SeasonStore store = newStore();
        store.replace(sample("2025"), 1L);
        store.replace(sample("2025"), 2L);
        assertEquals(3, store.meta("2025").orElseThrow().statRows());
        assertEquals(2, store.statValues("2025", "smpstats:blocks_broken").size());
        assertEquals(2L, store.meta("2025").orElseThrow().importedAt());
    }

    @Test
    void seasonsAreIndependent() throws Exception {
        SeasonStore store = newStore();
        store.replace(sample("2025"), 1L);
        store.replace(new SeasonData("2026", "x", Map.of(alex, new SeasonPlayer(alex, "Alex", 1L)),
                Map.of(alex, Map.of("smpstats:blocks_broken", 9.0)), Map.of()), 2L);
        assertEquals(Map.of(alex, 500.0, bea, 20.0), store.statValues("2025", "smpstats:blocks_broken"));
        assertEquals(Map.of(alex, 9.0), store.statValues("2026", "smpstats:blocks_broken"));
    }

    @Test
    void failedReplaceKeepsPreviousSeason() throws Exception {
        SeasonStore store = newStore();
        store.replace(sample("2025"), 1L);
        SeasonData broken = new SeasonData("2025", "x",
                Map.of(alex, new SeasonPlayer(alex, null, 1L)), // name NOT NULL -> constraint violation
                Map.of(), Map.of());

        assertThrows(SQLException.class, () -> store.replace(broken, 2L));

        assertEquals(1L, store.meta("2025").orElseThrow().importedAt());
        assertEquals(2, store.statValues("2025", "smpstats:blocks_broken").size());
    }
}
