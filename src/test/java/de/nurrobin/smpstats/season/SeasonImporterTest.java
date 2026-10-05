package de.nurrobin.smpstats.season;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeasonImporterTest {

    @TempDir
    Path dir;

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private void writeLegacyDb() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("stats.db"));
             Statement st = c.createStatement()) {
            st.execute("CREATE TABLE player_stats (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, playtime_ms INTEGER,"
                    + " blocks_broken INTEGER, biomes TEXT)");
            st.execute("INSERT INTO player_stats VALUES ('" + ALEX + "', 'Alex', 7200000, 500, '[\"PLAINS\"]')");
            st.execute("CREATE TABLE moments (uuid TEXT, type TEXT, started_at INTEGER)");
        }
    }

    private void writeVanilla() throws Exception {
        Path stats = Files.createDirectories(dir.resolve("world/stats"));
        Files.writeString(stats.resolve(ALEX + ".json"), "{\"stats\":{\"minecraft:custom\":{\"minecraft:jump\":12}}}");
        Files.writeString(stats.resolve(BEA + ".json"), "{\"stats\":{\"minecraft:custom\":{\"minecraft:play_time\":72000}}}");
        Path adv = Files.createDirectories(dir.resolve("world/advancements"));
        Files.writeString(adv.resolve(BEA + ".json"),
                "{\"minecraft:story/enter_the_nether\":{\"criteria\":{\"x\":\"2025-11-29 09:47:54 +0000\"},\"done\":true}}");
        Files.writeString(dir.resolve("usercache.json"), "[{\"uuid\":\"" + BEA + "\",\"name\":\"Bea\"}]");
    }

    @Test
    void mergesAllSourcesIntoOneSeason() throws Exception {
        writeLegacyDb();
        writeVanilla();

        SeasonImporter.Result r = new SeasonImporter().read("2025", dir);
        SeasonData data = r.data();

        assertEquals("2025", data.season());
        assertEquals(2, data.players().size());
        assertEquals("Alex", data.players().get(ALEX).name());
        assertEquals(7_200_000L, data.players().get(ALEX).playtimeMs());
        assertEquals("Bea", data.players().get(BEA).name());
        assertEquals(3_600_000L, data.players().get(BEA).playtimeMs(), "72000 ticks * 50 ms");
        assertEquals(500.0, data.stats().get(ALEX).get("smpstats:blocks_broken"));
        assertEquals(12.0, data.stats().get(ALEX).get("minecraft:custom/minecraft:jump"));
        assertTrue(data.advancements().get(BEA).containsKey("minecraft:story/enter_the_nether"));
        assertEquals("stats.db,world/stats,world/advancements,usercache.json", data.source());
        // the minimal test DB lacks most columns; that is the only expected hint
        assertTrue(r.problems().stream().allMatch(p -> p.contains("Spalten fehlen")), r.problems().toString());
    }

    @Test
    void missingPartsAreProblemsAndUnknownNamesGetFallback() throws Exception {
        Path stats = Files.createDirectories(dir.resolve("world/stats"));
        Files.writeString(stats.resolve(BEA + ".json"), "{\"stats\":{\"minecraft:custom\":{\"minecraft:jump\":1}}}");

        SeasonImporter.Result r = new SeasonImporter().read("2025", dir);

        assertEquals(1, r.data().players().size());
        assertEquals("unbekannt", r.data().players().get(BEA).name());
        assertEquals(0L, r.data().players().get(BEA).playtimeMs());
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("stats.db")), r.problems().toString());
        assertTrue(r.problems().stream().anyMatch(p -> p.contains("advancements")), r.problems().toString());
        assertEquals("world/stats", r.data().source());
    }
}
