package de.nurrobin.smpstats.season;

import de.nurrobin.smpstats.database.StatsStorage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SeasonStore {
    private static final int BATCH = 5000;
    private static final List<String> TABLES = List.of("season_stats", "season_advancements", "season_players", "season_meta");

    private final StatsStorage storage;

    public SeasonStore(StatsStorage storage) {
        this.storage = storage;
    }

    /** Replaces the whole season in one transaction and returns the number of stat rows written. */
    public int replace(SeasonData data, long importedAt) throws SQLException {
        return storage.withConnection(c -> {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                for (String table : TABLES) {
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM " + table + " WHERE season = ?")) {
                        del.setString(1, data.season());
                        del.executeUpdate();
                    }
                }
                insertPlayers(c, data);
                int rows = insertStats(c, data);
                insertAdvancements(c, data);
                try (PreparedStatement meta = c.prepareStatement(
                        "INSERT INTO season_meta(season, imported_at, source, players, stat_rows) VALUES (?, ?, ?, ?, ?)")) {
                    meta.setString(1, data.season());
                    meta.setLong(2, importedAt);
                    meta.setString(3, data.source());
                    meta.setInt(4, data.players().size());
                    meta.setInt(5, rows);
                    meta.executeUpdate();
                }
                c.commit();
                return rows;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        });
    }

    private static void insertPlayers(Connection c, SeasonData data) throws SQLException {
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO season_players(season, uuid, name, playtime_ms) VALUES (?, ?, ?, ?)")) {
            for (SeasonPlayer p : data.players().values()) {
                ins.setString(1, data.season());
                ins.setString(2, p.uuid().toString());
                ins.setString(3, p.name());
                ins.setLong(4, p.playtimeMs());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private static int insertStats(Connection c, SeasonData data) throws SQLException {
        int rows = 0;
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO season_stats(season, uuid, stat_key, value) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<UUID, Map<String, Double>> player : data.stats().entrySet()) {
                for (Map.Entry<String, Double> stat : player.getValue().entrySet()) {
                    ins.setString(1, data.season());
                    ins.setString(2, player.getKey().toString());
                    ins.setString(3, stat.getKey());
                    ins.setDouble(4, stat.getValue());
                    ins.addBatch();
                    if (++rows % BATCH == 0) {
                        ins.executeBatch();
                    }
                }
            }
            ins.executeBatch();
        }
        return rows;
    }

    private static void insertAdvancements(Connection c, SeasonData data) throws SQLException {
        int rows = 0;
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO season_advancements(season, uuid, advancement, achieved_at) VALUES (?, ?, ?, ?)")) {
            for (Map.Entry<UUID, Map<String, Long>> player : data.advancements().entrySet()) {
                for (Map.Entry<String, Long> adv : player.getValue().entrySet()) {
                    ins.setString(1, data.season());
                    ins.setString(2, player.getKey().toString());
                    ins.setString(3, adv.getKey());
                    ins.setLong(4, adv.getValue());
                    ins.addBatch();
                    if (++rows % BATCH == 0) {
                        ins.executeBatch();
                    }
                }
            }
            ins.executeBatch();
        }
    }

    public Optional<SeasonMeta> meta(String season) throws SQLException {
        return storage.withConnection(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT imported_at, source, players, stat_rows FROM season_meta WHERE season = ?")) {
                st.setString(1, season);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new SeasonMeta(season, rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4)));
                }
            }
        });
    }

    /** Values of one stat for all players of the season, only players with a value above zero. */
    public Map<UUID, Double> statValues(String season, String statKey) throws SQLException {
        return storage.withConnection(c -> {
            Map<UUID, Double> out = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT uuid, value FROM season_stats WHERE season = ? AND stat_key = ? AND value > 0")) {
                st.setString(1, season);
                st.setString(2, statKey);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.put(UUID.fromString(rs.getString(1)), rs.getDouble(2));
                    }
                }
            }
            return out;
        });
    }

    public Map<String, Double> playerStats(String season, UUID uuid) throws SQLException {
        return storage.withConnection(c -> {
            Map<String, Double> out = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT stat_key, value FROM season_stats WHERE season = ? AND uuid = ?")) {
                st.setString(1, season);
                st.setString(2, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.put(rs.getString(1), rs.getDouble(2));
                    }
                }
            }
            return out;
        });
    }

    public Map<UUID, Long> advancementTimes(String season, String advancement) throws SQLException {
        return storage.withConnection(c -> {
            Map<UUID, Long> out = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT uuid, achieved_at FROM season_advancements WHERE season = ? AND advancement = ?")) {
                st.setString(1, season);
                st.setString(2, advancement);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
                    }
                }
            }
            return out;
        });
    }

    /** All players of a season, keyed by uuid. */
    public Map<UUID, SeasonPlayer> players(String season) throws SQLException {
        return storage.withConnection(c -> {
            Map<UUID, SeasonPlayer> out = new HashMap<>();
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT uuid, name, playtime_ms FROM season_players WHERE season = ?")) {
                st.setString(1, season);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString(1));
                        out.put(uuid, new SeasonPlayer(uuid, rs.getString(2), rs.getLong(3)));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Stat rows of a season whose key equals one of {@code exactKeys} or starts with one of {@code prefixes},
     * grouped per player. Zero values are included, callers decide what they mean.
     */
    public Map<UUID, Map<String, Double>> statsMatching(String season, java.util.Collection<String> exactKeys,
                                                         java.util.Collection<String> prefixes) throws SQLException {
        return storage.withConnection(c -> {
            Map<UUID, Map<String, Double>> out = new HashMap<>();
            StringBuilder sql = new StringBuilder("SELECT uuid, stat_key, value FROM season_stats WHERE season = ? AND (0");
            for (int i = 0; i < exactKeys.size(); i++) {
                sql.append(" OR stat_key = ?");
            }
            for (int i = 0; i < prefixes.size(); i++) {
                sql.append(" OR stat_key LIKE ? ESCAPE '\\'");
            }
            sql.append(")");
            try (PreparedStatement st = c.prepareStatement(sql.toString())) {
                int idx = 1;
                st.setString(idx++, season);
                for (String key : exactKeys) {
                    st.setString(idx++, key);
                }
                for (String prefix : prefixes) {
                    st.setString(idx++, prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
                }
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.computeIfAbsent(UUID.fromString(rs.getString(1)), k -> new HashMap<>())
                                .put(rs.getString(2), rs.getDouble(3));
                    }
                }
            }
            return out;
        });
    }

    /** Completion times of the given advancements (or moment keys), grouped per player. */
    public Map<UUID, Map<String, Long>> advancementsOf(String season, java.util.Collection<String> keys) throws SQLException {
        return storage.withConnection(c -> {
            Map<UUID, Map<String, Long>> out = new HashMap<>();
            if (keys.isEmpty()) {
                return out;
            }
            String marks = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT uuid, advancement, achieved_at FROM season_advancements WHERE season = ? AND advancement IN ("
                            + marks + ")")) {
                int idx = 1;
                st.setString(idx++, season);
                for (String key : keys) {
                    st.setString(idx++, key);
                }
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        out.computeIfAbsent(UUID.fromString(rs.getString(1)), k -> new HashMap<>())
                                .put(rs.getString(2), rs.getLong(3));
                    }
                }
            }
            return out;
        });
    }

    public Optional<SeasonPlayer> player(String season, UUID uuid) throws SQLException {
        return storage.withConnection(c -> {
            try (PreparedStatement st = c.prepareStatement(
                    "SELECT name, playtime_ms FROM season_players WHERE season = ? AND uuid = ?")) {
                st.setString(1, season);
                st.setString(2, uuid.toString());
                try (ResultSet rs = st.executeQuery()) {
                    return rs.next() ? Optional.of(new SeasonPlayer(uuid, rs.getString(1), rs.getLong(2))) : Optional.empty();
                }
            }
        });
    }
}
