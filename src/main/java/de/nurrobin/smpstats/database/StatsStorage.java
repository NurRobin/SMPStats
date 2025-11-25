package de.nurrobin.smpstats.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.heatmap.HeatmapBin;
import de.nurrobin.smpstats.heatmap.HeatmapType;
import de.nurrobin.smpstats.moments.MomentEntry;
import de.nurrobin.smpstats.moments.MomentType;
import org.bukkit.plugin.Plugin;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class StatsStorage implements Closeable {
    private static final Type STRING_SET = new TypeToken<Set<String>>() {
    }.getType();

    private final Plugin plugin;
    private final Path databaseFile;
    private final Gson gson = new Gson();
    private Connection connection;

    public StatsStorage(Plugin plugin) {
        this.plugin = plugin;
        this.databaseFile = plugin.getDataFolder().toPath().resolve("stats.db");
    }

    public void init() throws SQLException, IOException {
        if (!Files.exists(databaseFile.getParent())) {
            Files.createDirectories(databaseFile.getParent());
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA journal_mode=WAL;");
            pragma.execute("PRAGMA synchronous=NORMAL;");
        }
        createTables();
    }

    private void createTables() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS player_stats (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    first_join INTEGER NOT NULL,
                    last_join INTEGER NOT NULL,
                    playtime_ms INTEGER NOT NULL DEFAULT 0,
                    deaths INTEGER NOT NULL DEFAULT 0,
                    last_death TEXT,
                    player_kills INTEGER NOT NULL DEFAULT 0,
                    mob_kills INTEGER NOT NULL DEFAULT 0,
                    blocks_placed INTEGER NOT NULL DEFAULT 0,
                    blocks_broken INTEGER NOT NULL DEFAULT 0,
                    dist_overworld REAL NOT NULL DEFAULT 0,
                    dist_nether REAL NOT NULL DEFAULT 0,
                    dist_end REAL NOT NULL DEFAULT 0,
                    biomes TEXT,
                    damage_dealt REAL NOT NULL DEFAULT 0,
                    damage_taken REAL NOT NULL DEFAULT 0,
                    items_crafted INTEGER NOT NULL DEFAULT 0,
                    items_consumed INTEGER NOT NULL DEFAULT 0
                );

                CREATE TABLE IF NOT EXISTS moments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL,
                    type TEXT NOT NULL,
                    title TEXT,
                    detail TEXT,
                    payload TEXT,
                    world TEXT,
                    x INTEGER,
                    y INTEGER,
                    z INTEGER,
                    started_at INTEGER NOT NULL,
                    ended_at INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS heatmap_bins (
                    type TEXT NOT NULL,
                    world TEXT NOT NULL,
                    chunk_x INTEGER NOT NULL,
                    chunk_z INTEGER NOT NULL,
                    count INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (type, world, chunk_x, chunk_z)
                );
                """;
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_name ON player_stats(name);");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_moments_type_time ON moments(type, started_at DESC);");
        }
    }

    public synchronized StatsRecord loadOrCreate(UUID uuid, String name) throws SQLException {
        Optional<StatsRecord> existing = load(uuid);
        if (existing.isPresent()) {
            StatsRecord record = existing.get();
            record.setName(name); // keep last seen name up to date
            return record;
        }

        long now = System.currentTimeMillis();
        StatsRecord record = new StatsRecord(uuid, name);
        record.setFirstJoin(now);
        record.setLastJoin(now);
        save(record);
        return record;
    }

    public synchronized Optional<StatsRecord> load(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM player_stats WHERE uuid = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRecord(rs));
                }
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<StatsRecord> loadByName(String name) throws SQLException {
        String sql = "SELECT * FROM player_stats WHERE lower(name) = lower(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRecord(rs));
                }
            }
        }
        return Optional.empty();
    }

    public synchronized List<StatsRecord> loadAll() throws SQLException {
        List<StatsRecord> records = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM player_stats")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    records.add(mapRecord(rs));
                }
            }
        }
        return records;
    }

    public synchronized void save(StatsRecord record) throws SQLException {
        String sql = """
                INSERT INTO player_stats (uuid, name, first_join, last_join, playtime_ms, deaths, last_death,
                                          player_kills, mob_kills, blocks_placed, blocks_broken,
                                          dist_overworld, dist_nether, dist_end, biomes,
                                          damage_dealt, damage_taken, items_crafted, items_consumed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    first_join = excluded.first_join,
                    last_join = excluded.last_join,
                    playtime_ms = excluded.playtime_ms,
                    deaths = excluded.deaths,
                    last_death = excluded.last_death,
                    player_kills = excluded.player_kills,
                    mob_kills = excluded.mob_kills,
                    blocks_placed = excluded.blocks_placed,
                    blocks_broken = excluded.blocks_broken,
                    dist_overworld = excluded.dist_overworld,
                    dist_nether = excluded.dist_nether,
                    dist_end = excluded.dist_end,
                    biomes = excluded.biomes,
                    damage_dealt = excluded.damage_dealt,
                    damage_taken = excluded.damage_taken,
                    items_crafted = excluded.items_crafted,
                    items_consumed = excluded.items_consumed;
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getUuid().toString());
            statement.setString(2, record.getName());
            statement.setLong(3, record.getFirstJoin());
            statement.setLong(4, record.getLastJoin());
            statement.setLong(5, record.getPlaytimeMillis());
            statement.setLong(6, record.getDeaths());
            statement.setString(7, record.getLastDeathCause());
            statement.setLong(8, record.getPlayerKills());
            statement.setLong(9, record.getMobKills());
            statement.setLong(10, record.getBlocksPlaced());
            statement.setLong(11, record.getBlocksBroken());
            statement.setDouble(12, record.getDistanceOverworld());
            statement.setDouble(13, record.getDistanceNether());
            statement.setDouble(14, record.getDistanceEnd());
            statement.setString(15, gson.toJson(record.getBiomesVisited()));
            statement.setDouble(16, record.getDamageDealt());
            statement.setDouble(17, record.getDamageTaken());
            statement.setLong(18, record.getItemsCrafted());
            statement.setLong(19, record.getItemsConsumed());
            statement.executeUpdate();
        }
    }

    private StatsRecord mapRecord(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        StatsRecord record = new StatsRecord(uuid, rs.getString("name"));
        record.setFirstJoin(rs.getLong("first_join"));
        record.setLastJoin(rs.getLong("last_join"));
        record.setPlaytimeMillis(rs.getLong("playtime_ms"));
        record.setDeaths(rs.getLong("deaths"));
        record.setLastDeathCause(rs.getString("last_death"));
        record.setPlayerKills(rs.getLong("player_kills"));
        record.setMobKills(rs.getLong("mob_kills"));
        record.setBlocksPlaced(rs.getLong("blocks_placed"));
        record.setBlocksBroken(rs.getLong("blocks_broken"));
        record.setDistanceOverworld(rs.getDouble("dist_overworld"));
        record.setDistanceNether(rs.getDouble("dist_nether"));
        record.setDistanceEnd(rs.getDouble("dist_end"));
        record.setBiomesVisited(parseBiomes(rs.getString("biomes")));
        record.setDamageDealt(rs.getDouble("damage_dealt"));
        record.setDamageTaken(rs.getDouble("damage_taken"));
        record.setItemsCrafted(rs.getLong("items_crafted"));
        record.setItemsConsumed(rs.getLong("items_consumed"));
        return record;
    }

    private Set<String> parseBiomes(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        Set<String> biomes = gson.fromJson(raw, STRING_SET);
        return biomes != null ? biomes : new LinkedHashSet<>();
    }

    public synchronized void saveMoment(MomentEntry entry) throws SQLException {
        String sql = """
                INSERT INTO moments (uuid, type, title, detail, payload, world, x, y, z, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.getPlayerId().toString());
            statement.setString(2, entry.getType().name());
            statement.setString(3, entry.getTitle());
            statement.setString(4, entry.getDetail());
            statement.setString(5, entry.getPayload());
            statement.setString(6, entry.getWorld());
            statement.setInt(7, entry.getX());
            statement.setInt(8, entry.getY());
            statement.setInt(9, entry.getZ());
            statement.setLong(10, entry.getStartedAt());
            statement.setLong(11, entry.getEndedAt());
            statement.executeUpdate();
        }
    }

    public synchronized List<MomentEntry> loadRecentMoments(int limit) throws SQLException {
        String sql = "SELECT * FROM moments ORDER BY started_at DESC LIMIT ?";
        List<MomentEntry> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMoment(rs));
                }
            }
        }
        return result;
    }

    private MomentEntry mapMoment(ResultSet rs) throws SQLException {
        return new MomentEntry(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                MomentType.valueOf(rs.getString("type")),
                rs.getString("title"),
                rs.getString("detail"),
                rs.getString("payload"),
                rs.getString("world"),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getLong("started_at"),
                rs.getLong("ended_at")
        );
    }

    public synchronized void incrementHeatmapBin(HeatmapType type, String world, int chunkX, int chunkZ, long delta) throws SQLException {
        String sql = """
                INSERT INTO heatmap_bins (type, world, chunk_x, chunk_z, count)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(type, world, chunk_x, chunk_z) DO UPDATE SET
                    count = count + excluded.count;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setString(2, world);
            statement.setInt(3, chunkX);
            statement.setInt(4, chunkZ);
            statement.setLong(5, delta);
            statement.executeUpdate();
        }
    }

    public synchronized List<HeatmapBin> loadHeatmapBins(HeatmapType type, int limit) throws SQLException {
        String sql = "SELECT * FROM heatmap_bins WHERE type = ? ORDER BY count DESC LIMIT ?";
        List<HeatmapBin> bins = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type.name());
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    bins.add(new HeatmapBin(
                            HeatmapType.valueOf(rs.getString("type")),
                            rs.getString("world"),
                            rs.getInt("chunk_x"),
                            rs.getInt("chunk_z"),
                            rs.getLong("count")
                    ));
                }
            }
        }
        return bins;
    }

    @Override
    public synchronized void close() throws IOException {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
            }
        }
    }
}
