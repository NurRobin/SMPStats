package de.nurrobin.smpstats.season;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDbReaderTest {

    @TempDir
    Path tempDir;

    private static final String ALEX = "00000000-0000-0000-0000-00000000000a";

    private Path legacyDb(boolean withMoments) throws Exception {
        Path db = tempDir.resolve("stats-" + UUID.randomUUID() + ".db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE player_stats (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, first_join INTEGER,
                      last_join INTEGER, playtime_ms INTEGER, deaths INTEGER, last_death TEXT, player_kills INTEGER,
                      mob_kills INTEGER, blocks_placed INTEGER, blocks_broken INTEGER, dist_overworld REAL,
                      dist_nether REAL, dist_end REAL, biomes TEXT, damage_dealt REAL, damage_taken REAL,
                      items_crafted INTEGER, items_consumed INTEGER)""");
            st.execute("INSERT INTO player_stats VALUES ('" + ALEX + "', 'Alex', 1, 2, 7200000, 3, 'FALL', 0, 40,"
                    + " 100, 500, 1234.5, 10.0, 0.0, '[\"PLAINS\",\"DESERT\"]', 50.5, 20.0, 7, 8)");
            st.execute("INSERT INTO player_stats VALUES ('kaputt', 'Kaputt', 1, 2, 1, 0, NULL, 0, 0, 0, 0, 0, 0, 0,"
                    + " '[]', 0, 0, 0, 0)");
            st.execute("INSERT INTO player_stats VALUES ('00000000-0000-0000-0000-00000000000b', 'Bea', 1, 2, 60000,"
                    + " 0, NULL, 0, 0, 0, 0, 0, 0, 0, 'kein json', 0, 0, 0, 0)");
            if (withMoments) {
                st.execute("CREATE TABLE moments (id INTEGER PRIMARY KEY, uuid TEXT, type TEXT, title TEXT, detail TEXT,"
                        + " payload TEXT, world TEXT, x INTEGER, y INTEGER, z INTEGER, started_at INTEGER, ended_at INTEGER)");
                st.execute("INSERT INTO moments(uuid, type, started_at) VALUES ('" + ALEX + "', 'wither_kill', 500)");
                st.execute("INSERT INTO moments(uuid, type, started_at) VALUES ('" + ALEX + "', 'wither_kill', 300)");
                st.execute("INSERT INTO moments(uuid, type, started_at) VALUES ('" + ALEX + "', 'first_death', 100)");
            }
        }
        return db;
    }

    private static byte[] sha(Path p) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));
    }

    @Test
    void readsPlayersStatsBiomesAndMoments() throws Exception {
        Path db = legacyDb(true);
        List<String> problems = new ArrayList<>();
        LegacyDbReader.Result r = LegacyDbReader.read(db, problems);

        UUID alex = UUID.fromString(ALEX);
        assertEquals("Alex", r.players().get(alex).name());
        assertEquals(7_200_000L, r.players().get(alex).playtimeMs());
        assertEquals(500.0, r.stats().get(alex).get("smpstats:blocks_broken"));
        assertEquals(1234.5, r.stats().get(alex).get("smpstats:dist_overworld"));
        assertEquals(2.0, r.stats().get(alex).get("smpstats:biomes_count"));
        assertEquals(2.0, r.stats().get(alex).get("smpstats:moments/wither_kill"));
        assertEquals(300L, r.firsts().get(alex).get("smpstats:moment/wither_kill"));
        assertEquals(100L, r.firsts().get(alex).get("smpstats:moment/first_death"));
        assertFalse(r.stats().get(alex).containsKey("smpstats:first_join"), "timestamps are not stats");
    }

    @Test
    void reportsBadRowsAndKeepsTheRest() throws Exception {
        List<String> problems = new ArrayList<>();
        LegacyDbReader.Result r = LegacyDbReader.read(legacyDb(true), problems);

        assertEquals(2, r.players().size(), "row with invalid uuid is skipped");
        assertTrue(problems.stream().anyMatch(p -> p.contains("kaputt")), problems.toString());
        UUID bea = UUID.fromString("00000000-0000-0000-0000-00000000000b");
        assertFalse(r.stats().get(bea).containsKey("smpstats:biomes_count"), "invalid biome json is skipped");
        assertTrue(problems.stream().anyMatch(p -> p.contains("Biome")), problems.toString());
    }

    @Test
    void missingMomentsTableIsAProblemNotAFailure() throws Exception {
        List<String> problems = new ArrayList<>();
        LegacyDbReader.Result r = LegacyDbReader.read(legacyDb(false), problems);

        assertEquals(2, r.players().size());
        assertTrue(r.firsts().isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.contains("moments")), problems.toString());
    }

    @Test
    void sourceFileStaysUnchanged() throws Exception {
        Path db = legacyDb(true);
        byte[] before = sha(db);
        LegacyDbReader.read(db, new ArrayList<>());
        assertArrayEquals(before, sha(db));
    }

    @Test
    void readsWalDatabaseFromReadOnlyFolderWithoutSideFiles() throws Exception {
        Path folder = Files.createDirectory(tempDir.resolve("readonly-" + UUID.randomUUID()));
        Path db = folder.resolve("stats.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("CREATE TABLE player_stats (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, playtime_ms INTEGER)");
            st.execute("INSERT INTO player_stats VALUES ('" + ALEX + "', 'Alex', 5)");
        }
        Files.deleteIfExists(folder.resolve("stats.db-wal"));
        Files.deleteIfExists(folder.resolve("stats.db-shm"));
        assertTrue(folder.toFile().setWritable(false));
        try {
            List<String> problems = new ArrayList<>();
            LegacyDbReader.Result r = LegacyDbReader.read(db, problems);

            assertEquals("Alex", r.players().get(UUID.fromString(ALEX)).name());
            assertTrue(problems.stream().noneMatch(p -> p.contains("CANTOPEN") || p.contains("player_stats nicht lesbar")),
                    problems.toString());
            assertFalse(Files.exists(folder.resolve("stats.db-shm")));
        } finally {
            folder.toFile().setWritable(true);
        }
    }

    @Test
    void warnsWhenAWalFileWithContentIsNextToTheDatabase() throws Exception {
        Path db = legacyDb(true);
        Files.write(db.resolveSibling(db.getFileName() + "-wal"), new byte[]{1, 2, 3});
        List<String> problems = new ArrayList<>();

        LegacyDbReader.read(db, problems);

        assertTrue(problems.stream().anyMatch(p -> p.contains("-wal")), problems.toString());
    }
}
