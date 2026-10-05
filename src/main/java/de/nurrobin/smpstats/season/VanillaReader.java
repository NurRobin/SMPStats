package de.nurrobin.smpstats.season;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Reads the vanilla per-player files of a world: stats, advancements and the usercache. */
final class VanillaReader {
    private static final DateTimeFormatter ADVANCEMENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final String RECIPES = "minecraft:recipes/";

    private VanillaReader() {
    }

    static Map<UUID, Map<String, Double>> readStats(Path statsDir, List<String> problems) {
        Map<UUID, Map<String, Double>> out = new HashMap<>();
        for (Path file : jsonFiles(statsDir, problems)) {
            Optional<UUID> uuid = uuidOf(file, problems);
            Optional<JsonObject> root = parse(file, problems);
            if (uuid.isEmpty() || root.isEmpty()) {
                continue;
            }
            JsonObject stats = root.get().getAsJsonObject("stats");
            if (stats == null) {
                problems.add(file.getFileName() + ": kein \"stats\"-Objekt");
                continue;
            }
            Map<String, Double> values = new HashMap<>();
            boolean badValue = false;
            for (Map.Entry<String, JsonElement> category : stats.entrySet()) {
                if (!category.getValue().isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> stat : category.getValue().getAsJsonObject().entrySet()) {
                    JsonElement v = stat.getValue();
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                        values.put(category.getKey() + "/" + stat.getKey(), v.getAsDouble());
                    } else {
                        badValue = true;
                    }
                }
            }
            if (badValue) {
                problems.add(file.getFileName() + ": nicht-numerische Werte übersprungen");
            }
            out.put(uuid.get(), values);
        }
        return out;
    }

    static Map<UUID, Map<String, Long>> readAdvancements(Path advancementsDir, List<String> problems) {
        Map<UUID, Map<String, Long>> out = new HashMap<>();
        for (Path file : jsonFiles(advancementsDir, problems)) {
            Optional<UUID> uuid = uuidOf(file, problems);
            Optional<JsonObject> root = parse(file, problems);
            if (uuid.isEmpty() || root.isEmpty()) {
                continue;
            }
            Map<String, Long> done = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.get().entrySet()) {
                if (entry.getKey().startsWith(RECIPES) || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject adv = entry.getValue().getAsJsonObject();
                if (!adv.has("done") || !adv.get("done").getAsBoolean() || !adv.has("criteria")) {
                    continue;
                }
                long latest = Long.MIN_VALUE;
                for (Map.Entry<String, JsonElement> criterion : adv.getAsJsonObject("criteria").entrySet()) {
                    try {
                        long t = OffsetDateTime.parse(criterion.getValue().getAsString(), ADVANCEMENT_TIME)
                                .toInstant().toEpochMilli();
                        latest = Math.max(latest, t);
                    } catch (DateTimeParseException | UnsupportedOperationException e) {
                        problems.add(file.getFileName() + ": Zeitpunkt nicht lesbar bei " + entry.getKey());
                    }
                }
                if (latest != Long.MIN_VALUE) {
                    done.put(entry.getKey(), latest);
                }
            }
            out.put(uuid.get(), done);
        }
        return out;
    }

    static Map<UUID, String> readUserCache(Path file, List<String> problems) {
        Map<UUID, String> out = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            problems.add("usercache.json fehlt (Namen nur aus stats.db)");
            return out;
        }
        try {
            for (JsonElement e : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                try {
                    out.put(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString());
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    // a broken cache entry only costs us one fallback name
                }
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            problems.add("usercache.json nicht lesbar: " + e.getMessage());
        }
        return out;
    }

    private static List<Path> jsonFiles(Path dir, List<String> problems) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            problems.add(dir.getFileName() + " nicht lesbar: " + e.getMessage());
            return List.of();
        }
    }

    private static Optional<UUID> uuidOf(Path file, List<String> problems) {
        String name = file.getFileName().toString();
        try {
            return Optional.of(UUID.fromString(name.substring(0, name.length() - ".json".length())));
        } catch (IllegalArgumentException e) {
            problems.add(name + ": Dateiname ist keine UUID, übersprungen");
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> parse(Path file, List<String> problems) {
        try {
            return Optional.of(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        } catch (IOException | JsonParseException | IllegalStateException e) {
            problems.add(file.getFileName() + ": kein gültiges JSON, übersprungen");
            return Optional.empty();
        }
    }
}
