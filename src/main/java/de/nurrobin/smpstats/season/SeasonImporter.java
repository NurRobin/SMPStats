package de.nurrobin.smpstats.season;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads one season folder (old SMPStats DB plus vanilla world files) into a {@link SeasonData}. */
public final class SeasonImporter {
    private static final String PLAY_TIME = "minecraft:custom/minecraft:play_time";
    private static final long MS_PER_TICK = 50L;

    public record Result(SeasonData data, List<String> problems) {
    }

    public Result read(String season, Path dir) {
        List<String> problems = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        LegacyDbReader.Result legacy = new LegacyDbReader.Result(Map.of(), Map.of(), Map.of());
        Path db = dir.resolve("stats.db");
        if (Files.isRegularFile(db)) {
            legacy = LegacyDbReader.read(db, problems);
            sources.add("stats.db");
        } else {
            problems.add("stats.db fehlt");
        }

        Map<UUID, Map<String, Double>> vanillaStats = Map.of();
        Path statsDir = dir.resolve("world").resolve("stats");
        if (Files.isDirectory(statsDir)) {
            vanillaStats = VanillaReader.readStats(statsDir, problems);
            sources.add("world/stats");
        } else {
            problems.add("world/stats fehlt");
        }

        Map<UUID, Map<String, Long>> vanillaAdv = Map.of();
        Path advDir = dir.resolve("world").resolve("advancements");
        if (Files.isDirectory(advDir)) {
            vanillaAdv = VanillaReader.readAdvancements(advDir, problems);
            sources.add("world/advancements");
        } else {
            problems.add("world/advancements fehlt");
        }

        Path cacheFile = dir.resolve("usercache.json");
        Map<UUID, String> names = Map.of();
        if (Files.isRegularFile(cacheFile)) {
            names = VanillaReader.readUserCache(cacheFile, problems);
            sources.add("usercache.json");
        }

        Map<UUID, Map<String, Double>> stats = merge(legacy.stats(), vanillaStats);
        Map<UUID, Map<String, Long>> advancements = merge(legacy.firsts(), vanillaAdv);

        Set<UUID> everyone = new HashSet<>(legacy.players().keySet());
        everyone.addAll(stats.keySet());
        everyone.addAll(advancements.keySet());

        Map<UUID, SeasonPlayer> players = new HashMap<>();
        for (UUID uuid : everyone) {
            SeasonPlayer old = legacy.players().get(uuid);
            String name = old != null && old.name() != null ? old.name() : names.getOrDefault(uuid, "unbekannt");
            long playtime;
            if (old != null) {
                playtime = old.playtimeMs();
            } else {
                double ticks = stats.getOrDefault(uuid, Map.of()).getOrDefault(PLAY_TIME, 0.0);
                playtime = (long) ticks * MS_PER_TICK;
            }
            players.put(uuid, new SeasonPlayer(uuid, name, playtime));
        }
        return new Result(new SeasonData(season, String.join(",", sources), players, stats, advancements), problems);
    }

    private static <V> Map<UUID, Map<String, V>> merge(Map<UUID, Map<String, V>> first, Map<UUID, Map<String, V>> second) {
        Map<UUID, Map<String, V>> out = new HashMap<>();
        first.forEach((uuid, values) -> out.computeIfAbsent(uuid, k -> new HashMap<>()).putAll(values));
        second.forEach((uuid, values) -> out.computeIfAbsent(uuid, k -> new HashMap<>()).putAll(values));
        return out;
    }
}
