package de.nurrobin.smpstats.season;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VanillaReaderTest {

    @TempDir
    Path tempDir;

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void readsStatsWithCategoryKeys() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("stats"));
        Files.writeString(dir.resolve(ALEX + ".json"), """
                {"stats":{"minecraft:custom":{"minecraft:jump":120,"minecraft:aviate_one_cm":500000},
                          "minecraft:mined":{"minecraft:deepslate":42}},"DataVersion":4556}""");
        List<String> problems = new ArrayList<>();

        Map<UUID, Map<String, Double>> stats = VanillaReader.readStats(dir, problems);

        assertEquals(120.0, stats.get(ALEX).get("minecraft:custom/minecraft:jump"));
        assertEquals(500000.0, stats.get(ALEX).get("minecraft:custom/minecraft:aviate_one_cm"));
        assertEquals(42.0, stats.get(ALEX).get("minecraft:mined/minecraft:deepslate"));
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void brokenStatsFilesAreReportedAndSkipped() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("stats-bad"));
        Files.writeString(dir.resolve(ALEX + ".json"), "{\"stats\":{\"minecraft:custom\":{\"minecraft:jump\":\"viel\",\"minecraft:deaths\":3}}}");
        Files.writeString(dir.resolve("kein-uuid.json"), "{\"stats\":{}}");
        Files.writeString(dir.resolve(UUID.randomUUID() + ".json"), "{ nicht json");
        Files.writeString(dir.resolve("notes.txt"), "ignorieren");
        List<String> problems = new ArrayList<>();

        Map<UUID, Map<String, Double>> stats = VanillaReader.readStats(dir, problems);

        assertEquals(Map.of("minecraft:custom/minecraft:deaths", 3.0), stats.get(ALEX));
        assertEquals(1, stats.size());
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void readsOnlyCompletedNonRecipeAdvancementsAtLatestCriterion() throws Exception {
        Path dir = Files.createDirectories(tempDir.resolve("adv"));
        Files.writeString(dir.resolve(ALEX + ".json"), """
                {
                  "minecraft:story/enter_the_nether": {"criteria": {"entered_nether": "2025-11-29 09:47:54 +0000"}, "done": true},
                  "minecraft:adventure/adventuring_time": {"criteria": {"minecraft:plains": "2025-11-28 10:00:00 +0000",
                      "minecraft:desert": "2025-12-03 18:30:00 +0100"}, "done": true},
                  "minecraft:story/mine_diamond": {"criteria": {"diamond": "2025-11-30 12:00:00 +0000"}, "done": false},
                  "minecraft:recipes/misc/torch": {"criteria": {"has_coal": "2025-11-28 10:00:00 +0000"}, "done": true},
                  "DataVersion": 4556
                }""");
        List<String> problems = new ArrayList<>();

        Map<UUID, Map<String, Long>> adv = VanillaReader.readAdvancements(dir, problems);

        Map<String, Long> alex = adv.get(ALEX);
        assertEquals(2, alex.size(), alex.toString());
        assertEquals(OffsetDateTime.parse("2025-11-29T09:47:54Z").toInstant().toEpochMilli(),
                alex.get("minecraft:story/enter_the_nether"));
        assertEquals(OffsetDateTime.parse("2025-12-03T18:30:00+01:00").toInstant().toEpochMilli(),
                alex.get("minecraft:adventure/adventuring_time"));
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void readsUserCacheAndToleratesMissingFile() throws Exception {
        Path file = tempDir.resolve("usercache.json");
        Files.writeString(file, "[{\"uuid\":\"" + ALEX + "\",\"name\":\"Alex\",\"expiresOn\":\"2026-02-22 16:13:18 +0000\"},"
                + "{\"uuid\":\"kaputt\",\"name\":\"X\"}]");
        List<String> problems = new ArrayList<>();

        assertEquals(Map.of(ALEX, "Alex"), VanillaReader.readUserCache(file, problems));
        assertTrue(VanillaReader.readUserCache(tempDir.resolve("fehlt.json"), problems).isEmpty());
        assertEquals(1, problems.size(), problems.toString());
    }
}
