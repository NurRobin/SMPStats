package de.nurrobin.smpstats.season;

import com.google.gson.JsonArray;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reads a previous season's SMPStats database without modifying it. */
final class LegacyDbReader {
    static final List<String> NUMERIC_COLUMNS = List.of("playtime_ms", "deaths", "player_kills", "mob_kills",
            "blocks_placed", "blocks_broken", "dist_overworld", "dist_nether", "dist_end", "damage_dealt",
            "damage_taken", "items_crafted", "items_consumed");

    record Result(Map<UUID, SeasonPlayer> players,
                  Map<UUID, Map<String, Double>> stats,
                  Map<UUID, Map<String, Long>> firsts) {
    }

    private LegacyDbReader() {
    }

    static Result read(Path dbFile, List<String> problems) {
        Map<UUID, SeasonPlayer> players = new HashMap<>();
        Map<UUID, Map<String, Double>> stats = new HashMap<>();
        Map<UUID, Map<String, Long>> firsts = new HashMap<>();
        warnAboutPendingWal(dbFile, problems);
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        // immutable: no locks and no -shm/-wal side files, so read-only folders work and the source stays untouched
        String url = "jdbc:sqlite:" + dbFile.toAbsolutePath().toUri() + "?immutable=1";
        try (Connection c = DriverManager.getConnection(url, config.toProperties())) {
            readPlayers(c, players, stats, problems);
            readMoments(c, stats, firsts, problems);
        } catch (SQLException e) {
            problems.add("stats.db nicht lesbar: " + e.getMessage());
        }
        return new Result(players, stats, firsts);
    }

    private static void warnAboutPendingWal(Path dbFile, List<String> problems) {
        Path wal = dbFile.resolveSibling(dbFile.getFileName() + "-wal");
        try {
            if (Files.isRegularFile(wal) && Files.size(wal) > 0) {
                problems.add("stats.db: " + wal.getFileName() + " ist nicht leer und wird ignoriert;"
                        + " die DB vorher mit 'sqlite3 stats.db \"PRAGMA wal_checkpoint(TRUNCATE);\"' zusammenführen");
            }
        } catch (IOException e) {
            problems.add("stats.db: " + wal.getFileName() + " nicht prüfbar: " + e.getMessage());
        }
    }

    private static void readPlayers(Connection c, Map<UUID, SeasonPlayer> players,
                                    Map<UUID, Map<String, Double>> stats, List<String> problems) {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM player_stats")) {
            Set<String> columns = new HashSet<>();
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                columns.add(md.getColumnName(i).toLowerCase());
            }
            boolean biomeProblemReported = false;
            while (rs.next()) {
                String rawUuid = rs.getString("uuid");
                UUID uuid;
                try {
                    uuid = UUID.fromString(rawUuid);
                } catch (IllegalArgumentException e) {
                    problems.add("stats.db: Zeile mit ungültiger UUID übersprungen: " + rawUuid);
                    continue;
                }
                Map<String, Double> values = new HashMap<>();
                for (String column : NUMERIC_COLUMNS) {
                    if (columns.contains(column)) {
                        values.put("smpstats:" + column, rs.getDouble(column));
                    }
                }
                if (columns.contains("biomes")) {
                    String biomes = rs.getString("biomes");
                    try {
                        JsonArray array = JsonParser.parseString(biomes == null ? "[]" : biomes).getAsJsonArray();
                        values.put("smpstats:biomes_count", (double) array.size());
                    } catch (JsonParseException | IllegalStateException e) {
                        if (!biomeProblemReported) {
                            problems.add("stats.db: Biome-Liste nicht lesbar (z. B. bei " + uuid + "), übersprungen");
                            biomeProblemReported = true;
                        }
                    }
                }
                long playtime = columns.contains("playtime_ms") ? rs.getLong("playtime_ms") : 0L;
                players.put(uuid, new SeasonPlayer(uuid, rs.getString("name"), playtime));
                stats.put(uuid, values);
            }
            if (!columns.containsAll(NUMERIC_COLUMNS)) {
                Set<String> missing = new HashSet<>(NUMERIC_COLUMNS);
                missing.removeAll(columns);
                problems.add("stats.db: Spalten fehlen und wurden übersprungen: " + missing);
            }
        } catch (SQLException e) {
            problems.add("stats.db: player_stats nicht lesbar: " + e.getMessage());
        }
    }

    private static void readMoments(Connection c, Map<UUID, Map<String, Double>> stats,
                                    Map<UUID, Map<String, Long>> firsts, List<String> problems) {
        String sql = "SELECT uuid, type, COUNT(*), MIN(started_at) FROM moments GROUP BY uuid, type";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(rs.getString(1));
                } catch (IllegalArgumentException | NullPointerException e) {
                    continue;
                }
                String type = rs.getString(2);
                stats.computeIfAbsent(uuid, k -> new HashMap<>()).put("smpstats:moments/" + type, (double) rs.getLong(3));
                firsts.computeIfAbsent(uuid, k -> new HashMap<>()).put("smpstats:moment/" + type, rs.getLong(4));
            }
        } catch (SQLException e) {
            problems.add("stats.db: moments nicht lesbar: " + e.getMessage());
        }
    }
}
