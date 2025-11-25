package de.nurrobin.smpstats.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.heatmap.HeatmapBin;
import de.nurrobin.smpstats.moments.MomentEntry;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class StatsStorage implements Closeable {
    private static final int SCHEMA_VERSION = 4;
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
        applyMigrations();
    }

    private void applyMigrations() throws SQLException {
        int currentVersion = getUserVersion();
        if (currentVersion > SCHEMA_VERSION) {
            String msg = "Database schema (" + currentVersion + ") is newer than plugin supports (" + SCHEMA_VERSION + ").";
            plugin.getLogger().severe(msg);
            notifyAdmins(msg);
            throw new SQLException(msg);
        }
        connection.setAutoCommit(false);
        try {
            if (currentVersion == 0) {
                createBaseTables();
                currentVersion = 1;
            }
            if (currentVersion == 1) {
                addMomentsAndHeatmapTables();
                currentVersion = 2;
            }
            if (currentVersion == 2) {
                addSocialTimelineTables();
                currentVersion = 3;
            }
            if (currentVersion == 3) {
                addDeathReplayItemsColumn();
                currentVersion = 4;
            }
            setUserVersion(currentVersion);
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
        if (currentVersion < SCHEMA_VERSION) {
            setUserVersion(SCHEMA_VERSION);
        }
    }

    private int getUserVersion() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version;")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private void setUserVersion(int version) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA user_version=" + version + ";");
        }
    }

    private void createBaseTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
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
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_player_name ON player_stats(name);");
        }
    }

    private void addMomentsAndHeatmapTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
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
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_moments_type_time ON moments(type, started_at DESC);");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS heatmap_bins (
                        type TEXT NOT NULL,
                        world TEXT NOT NULL,
                        chunk_x INTEGER NOT NULL,
                        chunk_z INTEGER NOT NULL,
                        count INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (type, world, chunk_x, chunk_z)
                    );
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS heatmap_hotspots (
                        type TEXT NOT NULL,
                        hotspot TEXT NOT NULL,
                        world TEXT NOT NULL,
                        count INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (type, hotspot, world)
                    );
                    """);
        }
    }

    private void addSocialTimelineTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS social_pairs (
                        uuid_a TEXT NOT NULL,
                        uuid_b TEXT NOT NULL,
                        seconds INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid_a, uuid_b)
                    );
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS timeline_daily (
                        uuid TEXT NOT NULL,
                        day TEXT NOT NULL,
                        playtime_ms INTEGER NOT NULL,
                        blocks_broken INTEGER NOT NULL,
                        blocks_placed INTEGER NOT NULL,
                        player_kills INTEGER NOT NULL,
                        mob_kills INTEGER NOT NULL,
                        deaths INTEGER NOT NULL,
                        distance_overworld REAL NOT NULL,
                        distance_nether REAL NOT NULL,
                        distance_end REAL NOT NULL,
                        damage_dealt REAL NOT NULL,
                        damage_taken REAL NOT NULL,
                        items_crafted INTEGER NOT NULL,
                        items_consumed INTEGER NOT NULL,
                        PRIMARY KEY (uuid, day)
                    );
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS death_replays (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        ts INTEGER NOT NULL,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        cause TEXT,
                        health REAL,
                        world TEXT,
                        x INTEGER,
                        y INTEGER,
                        z INTEGER,
                        fall_distance REAL,
                        value REAL,
                        nearby_players TEXT,
                        nearby_mobs TEXT,
                        inventory TEXT
                    );
                    """);
        }
    }

    private void addDeathReplayItemsColumn() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE death_replays ADD COLUMN inventory TEXT;");
        } catch (SQLException e) {
            if (!e.getMessage().contains("duplicate column")) {
                throw e;
            }
        }
    }

    private void notifyAdmins(String msg) {
        plugin.getLogger().severe(msg);
        plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.isOp() || p.hasPermission("smpstats.reload"))
                .forEach(p -> p.sendMessage(org.bukkit.ChatColor.RED + "[SMPStats] " + msg));
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
            statement.setString(2, entry.getType());
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
                rs.getString("type"),
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

    public synchronized void incrementHeatmapBin(String type, String world, int chunkX, int chunkZ, long delta) throws SQLException {
        String sql = """
                INSERT INTO heatmap_bins (type, world, chunk_x, chunk_z, count)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(type, world, chunk_x, chunk_z) DO UPDATE SET
                    count = count + excluded.count;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setString(2, world);
            statement.setInt(3, chunkX);
            statement.setInt(4, chunkZ);
            statement.setLong(5, delta);
            statement.executeUpdate();
        }
    }

    public synchronized List<HeatmapBin> loadHeatmapBins(String type, int limit) throws SQLException {
        String sql = "SELECT * FROM heatmap_bins WHERE type = ? ORDER BY count DESC LIMIT ?";
        List<HeatmapBin> bins = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    bins.add(new HeatmapBin(
                            rs.getString("type"),
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

    public synchronized void incrementHotspot(String type, String hotspot, String world, long delta) throws SQLException {
        String sql = """
                INSERT INTO heatmap_hotspots (type, hotspot, world, count)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(type, hotspot, world) DO UPDATE SET
                    count = count + excluded.count;
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setString(2, hotspot);
            statement.setString(3, world);
            statement.setLong(4, delta);
            statement.executeUpdate();
        }
    }

    public synchronized Map<String, Long> loadHotspotCounts(String type) throws SQLException {
        String sql = "SELECT hotspot, count FROM heatmap_hotspots WHERE type = ? ORDER BY count DESC";
        Map<String, Long> map = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("hotspot"), rs.getLong("count"));
                }
            }
        }
        return map;
    }

    public synchronized boolean hasMoment(UUID playerId, String type) throws SQLException {
        String sql = "SELECT 1 FROM moments WHERE uuid = ? AND type = ? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, type);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
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

    public synchronized List<MomentEntry> loadMomentsSince(long sinceMillis, int limit) throws SQLException {
        String sql = "SELECT * FROM moments WHERE started_at >= ? ORDER BY started_at ASC LIMIT ?";
        List<MomentEntry> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceMillis);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMoment(rs));
                }
            }
        }
        return result;
    }

    public synchronized List<MomentEntry> queryMoments(UUID playerId, String type, long sinceMillis, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM moments WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (playerId != null) {
            sql.append(" AND uuid = ?");
            params.add(playerId.toString());
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        if (sinceMillis > 0) {
            sql.append(" AND started_at >= ?");
            params.add(sinceMillis);
        }
        sql.append(" ORDER BY started_at DESC");
        if (limit > 0) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }

        List<MomentEntry> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMoment(rs));
                }
            }
        }
        return result;
    }

    public synchronized void incrementSocialPair(UUID a, UUID b, long seconds) throws SQLException {
        String sql = """
                INSERT INTO social_pairs (uuid_a, uuid_b, seconds)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid_a, uuid_b) DO UPDATE SET
                    seconds = seconds + excluded.seconds;
                """;
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, a.toString());
            st.setString(2, b.toString());
            st.setLong(3, seconds);
            st.executeUpdate();
        }
    }

    public synchronized List<Map.Entry<String, Long>> loadTopSocial(int limit) throws SQLException {
        String sql = "SELECT uuid_a, uuid_b, seconds FROM social_pairs ORDER BY seconds DESC LIMIT ?";
        List<Map.Entry<String, Long>> list = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("uuid_a") + "," + rs.getString("uuid_b");
                    list.add(Map.entry(key, rs.getLong("seconds")));
                }
            }
        }
        return list;
    }

    public synchronized void upsertTimeline(StatsRecord record, java.time.LocalDate day) throws SQLException {
        String sql = """
                INSERT INTO timeline_daily (uuid, day, playtime_ms, blocks_broken, blocks_placed, player_kills, mob_kills, deaths,
                                            distance_overworld, distance_nether, distance_end,
                                            damage_dealt, damage_taken, items_crafted, items_consumed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, day) DO UPDATE SET
                    playtime_ms = excluded.playtime_ms,
                    blocks_broken = excluded.blocks_broken,
                    blocks_placed = excluded.blocks_placed,
                    player_kills = excluded.player_kills,
                    mob_kills = excluded.mob_kills,
                    deaths = excluded.deaths,
                    distance_overworld = excluded.distance_overworld,
                    distance_nether = excluded.distance_nether,
                    distance_end = excluded.distance_end,
                    damage_dealt = excluded.damage_dealt,
                    damage_taken = excluded.damage_taken,
                    items_crafted = excluded.items_crafted,
                    items_consumed = excluded.items_consumed;
                """;
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, record.getUuid().toString());
            st.setString(2, day.toString());
            st.setLong(3, record.getPlaytimeMillis());
            st.setLong(4, record.getBlocksBroken());
            st.setLong(5, record.getBlocksPlaced());
            st.setLong(6, record.getPlayerKills());
            st.setLong(7, record.getMobKills());
            st.setLong(8, record.getDeaths());
            st.setDouble(9, record.getDistanceOverworld());
            st.setDouble(10, record.getDistanceNether());
            st.setDouble(11, record.getDistanceEnd());
            st.setDouble(12, record.getDamageDealt());
            st.setDouble(13, record.getDamageTaken());
            st.setLong(14, record.getItemsCrafted());
            st.setLong(15, record.getItemsConsumed());
            st.executeUpdate();
        }
    }

    public synchronized List<Map<String, Object>> loadTimeline(UUID uuid, int limit) throws SQLException {
        String sql = "SELECT * FROM timeline_daily WHERE uuid = ? ORDER BY day DESC LIMIT ?";
        List<Map<String, Object>> result = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, uuid.toString());
            st.setInt(2, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("day", rs.getString("day"));
                    row.put("playtime_ms", rs.getLong("playtime_ms"));
                    row.put("blocks_broken", rs.getLong("blocks_broken"));
                    row.put("blocks_placed", rs.getLong("blocks_placed"));
                    row.put("player_kills", rs.getLong("player_kills"));
                    row.put("mob_kills", rs.getLong("mob_kills"));
                    row.put("deaths", rs.getLong("deaths"));
                    row.put("distance_overworld", rs.getDouble("distance_overworld"));
                    row.put("distance_nether", rs.getDouble("distance_nether"));
                    row.put("distance_end", rs.getDouble("distance_end"));
                    row.put("damage_dealt", rs.getDouble("damage_dealt"));
                    row.put("damage_taken", rs.getDouble("damage_taken"));
                    row.put("items_crafted", rs.getLong("items_crafted"));
                    row.put("items_consumed", rs.getLong("items_consumed"));
                    result.add(row);
                }
            }
        }
        return result;
    }

    public synchronized void saveDeathReplay(de.nurrobin.smpstats.timeline.DeathReplayEntry entry) throws SQLException {
        String sql = """
                INSERT INTO death_replays (ts, uuid, name, cause, health, world, x, y, z, fall_distance, value, nearby_players, nearby_mobs, inventory)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setLong(1, entry.timestamp());
            st.setString(2, entry.uuid());
            st.setString(3, entry.name());
            st.setString(4, entry.cause());
            st.setDouble(5, entry.health());
            st.setString(6, entry.world());
            st.setInt(7, entry.x());
            st.setInt(8, entry.y());
            st.setInt(9, entry.z());
            st.setDouble(10, entry.fallDistance());
            st.setDouble(11, 0); // value not used
            st.setString(12, gson.toJson(entry.nearbyPlayers()));
            st.setString(13, gson.toJson(entry.nearbyMobs()));
            st.setString(14, gson.toJson(entry.inventory()));
            st.executeUpdate();
        }
    }

    public synchronized List<de.nurrobin.smpstats.timeline.DeathReplayEntry> loadDeathReplays(int limit) throws SQLException {
        String sql = "SELECT * FROM death_replays ORDER BY ts DESC LIMIT ?";
        List<de.nurrobin.smpstats.timeline.DeathReplayEntry> list = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setInt(1, limit);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    list.add(new de.nurrobin.smpstats.timeline.DeathReplayEntry(
                            rs.getLong("ts"),
                            rs.getString("uuid"),
                            rs.getString("name"),
                            rs.getString("cause"),
                            rs.getDouble("health"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z"),
                            rs.getDouble("fall_distance"),
                            gson.fromJson(rs.getString("nearby_players"), new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType()),
                            gson.fromJson(rs.getString("nearby_mobs"), new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType()),
                            gson.fromJson(rs.getString("inventory"), new com.google.gson.reflect.TypeToken<java.util.List<String>>(){}.getType())
                    ));
                }
            }
        }
        return list;
    }
}
