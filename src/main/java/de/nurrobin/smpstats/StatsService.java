package de.nurrobin.smpstats;

import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.World;
import org.bukkit.entity.Player;
import de.nurrobin.smpstats.skills.SkillCalculator;
import de.nurrobin.smpstats.skills.SkillProfile;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatsService {
    private final SMPStats plugin;
    private final StatsStorage storage;
    private Settings settings;
    private SkillCalculator skillCalculator;
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public StatsService(SMPStats plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
        this.skillCalculator = new SkillCalculator(settings.getSkillWeights());
    }

    public StatsStorage getStorage() {
        return storage;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
        this.skillCalculator.updateWeights(settings.getSkillWeights());
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        StatsRecord record;
        boolean loaded;
        try {
            record = storage.loadOrCreate(uuid, player.getName());
            loaded = true;
        } catch (SQLException | RuntimeException e) {
            // Track into a blank record but never persist it as-is: saving it would overwrite the
            // stored history with zeros. The load is retried on the next save and merged then.
            plugin.getLogger().severe("Could not load stats for " + player.getName() + ": " + e.getMessage()
                    + " - tracking in memory until the database answers again.");
            record = new StatsRecord(uuid, player.getName());
            record.setFirstJoin(System.currentTimeMillis());
            loaded = false;
        }

        record.setLastJoin(System.currentTimeMillis());
        sessions.put(uuid, new PlayerSession(record, loaded));
    }

    public void handleQuit(Player player) {
        PlayerSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        StatsRecord copy;
        synchronized (session) {
            session.updatePlaytime(now);
            session.getRecord().setLastJoin(now);
            copy = session.getRecord().copy();
        }
        persist(session, copy);
    }

    /**
     * Saves every online player. Runs on the autosave thread, so each record is copied under the
     * session lock and only the copy is handed to the database.
     */
    public void flushOnline() {
        long now = System.currentTimeMillis();
        for (PlayerSession session : sessions.values()) {
            StatsRecord copy;
            synchronized (session) {
                session.updatePlaytime(now);
                copy = session.getRecord().copy();
            }
            persist(session, copy);
        }
    }
    public void shutdown() {
        flushOnline();
    }

    public Optional<StatsRecord> getStats(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            return Optional.of(session.snapshot());
        }
        try {
            return storage.load(uuid);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load stats for " + uuid + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<StatsRecord> getStatsByName(String name) {
        for (PlayerSession session : sessions.values()) {
            if (session.getRecord().getName().equalsIgnoreCase(name)) {
                return Optional.of(session.snapshot());
            }
        }
        try {
            return storage.loadByName(name);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load stats for player " + name + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<StatsRecord> getAllStats() {
        List<StatsRecord> all = new ArrayList<>();
        try {
            all.addAll(storage.loadAll());
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load all stats: " + e.getMessage());
        }
        // Replace DB versions with live sessions
        for (PlayerSession session : sessions.values()) {
            all.removeIf(r -> r.getUuid().equals(session.getRecord().getUuid()));
            all.add(session.snapshot());
        }
        all.sort(Comparator.comparing(StatsRecord::getName, String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    public Optional<SkillProfile> getSkillProfile(UUID uuid) {
        Optional<StatsRecord> stats = getStats(uuid);
        return stats.map(skillCalculator::calculate);
    }

    /**
     * Returns session delta information for a player if they have an active session.
     * @param uuid The player's UUID
     * @return Optional containing SessionDelta if player has active session
     */
    public Optional<SessionDelta> getSessionDelta(UUID uuid) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) {
            return Optional.empty();
        }
        StatsRecord current = session.snapshot();
        StatsRecord start = session.getSessionStartSnapshot();
        long duration = System.currentTimeMillis() - session.getSessionStartTime();
        return Optional.of(new SessionDelta(start, current, duration));
    }

    /**
     * Represents the delta between session start and current stats.
     */
    public record SessionDelta(StatsRecord start, StatsRecord current, long durationMillis) {
        public long deltaKills() {
            return (current.getPlayerKills() + current.getMobKills()) 
                 - (start.getPlayerKills() + start.getMobKills());
        }
        
        public long deltaDeaths() {
            return current.getDeaths() - start.getDeaths();
        }
        
        public long deltaBlocks() {
            return (current.getBlocksBroken() + current.getBlocksPlaced())
                 - (start.getBlocksBroken() + start.getBlocksPlaced());
        }
        
        public double deltaDistance() {
            return (current.getDistanceOverworld() + current.getDistanceNether() + current.getDistanceEnd())
                 - (start.getDistanceOverworld() + start.getDistanceNether() + start.getDistanceEnd());
        }
        
        public int deltaBiomes() {
            int startBiomes = start.getBiomesVisited() != null ? start.getBiomesVisited().size() : 0;
            int currentBiomes = current.getBiomesVisited() != null ? current.getBiomesVisited().size() : 0;
            return currentBiomes - startBiomes;
        }
    }

    public boolean resetStats(UUID uuid) {
        return editRecord(uuid, StatsService::clearCounters);
    }

    private static void clearCounters(StatsRecord record) {
        record.setPlaytimeMillis(0);
        record.setDeaths(0);
        record.setLastDeathCause(null);
        record.setPlayerKills(0);
        record.setMobKills(0);
        record.setBlocksPlaced(0);
        record.setBlocksBroken(0);
        record.setDistanceOverworld(0);
        record.setDistanceNether(0);
        record.setDistanceEnd(0);
        record.setBiomesVisited(new java.util.LinkedHashSet<>());
        record.setDamageDealt(0);
        record.setDamageTaken(0);
        record.setItemsCrafted(0);
        record.setItemsConsumed(0);
    }

    public boolean setStat(UUID uuid, de.nurrobin.smpstats.commands.StatField field, double value) {
        return editRecord(uuid, record -> field.apply(record, value));
    }

    private boolean editRecord(UUID uuid, java.util.function.Consumer<StatsRecord> edit) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            StatsRecord copy;
            synchronized (session) {
                session.foldStoredBase();
                edit.accept(session.getRecord());
                session.resetSessionStart();
                copy = session.getRecord().copy();
            }
            persist(session, copy);
            return true;
        }
        try {
            Optional<StatsRecord> fromDb = storage.load(uuid);
            if (fromDb.isEmpty()) {
                return false;
            }
            edit.accept(fromDb.get());
            save(fromDb.get());
            return true;
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load stats for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    public List<String> getOnlineNames() {
        return sessions.values().stream()
                .map(s -> s.getRecord().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void addBlocksBroken(UUID uuid) {
        withSession(uuid, StatsRecord::incrementBlocksBroken);
    }

    public void addBlocksPlaced(UUID uuid) {
        withSession(uuid, StatsRecord::incrementBlocksPlaced);
    }

    public void addDeath(UUID uuid, String cause) {
        withSession(uuid, record -> {
            record.incrementDeaths();
            record.setLastDeathCause(cause);
        });
    }

    public void addPlayerKill(UUID uuid) {
        if (!settings.isTrackKills()) {
            return;
        }
        withSession(uuid, StatsRecord::incrementPlayerKills);
    }

    public void addMobKill(UUID uuid) {
        if (!settings.isTrackKills()) {
            return;
        }
        withSession(uuid, StatsRecord::incrementMobKills);
    }

    public void addDistance(UUID uuid, World.Environment environment, double distance) {
        if (!settings.isTrackMovement()) {
            return;
        }
        withSession(uuid, record -> {
            switch (environment) {
                case NETHER -> record.addDistanceNether(distance);
                case THE_END -> record.addDistanceEnd(distance);
                default -> record.addDistanceOverworld(distance);
            }
        });
    }

    public void addBiome(UUID uuid, String biome) {
        if (!settings.isTrackBiomes()) {
            return;
        }
        withSession(uuid, record -> record.addBiome(biome));
    }

    public void addDamageDealt(UUID uuid, double damage) {
        if (!settings.isTrackDamage()) {
            return;
        }
        withSession(uuid, record -> record.addDamageDealt(damage));
    }

    public void addDamageTaken(UUID uuid, double damage) {
        if (!settings.isTrackDamage()) {
            return;
        }
        withSession(uuid, record -> record.addDamageTaken(damage));
    }

    public void addCrafted(UUID uuid, long amount) {
        if (!settings.isTrackCrafting()) {
            return;
        }
        withSession(uuid, record -> record.incrementItemsCrafted(amount));
    }

    public void addConsumed(UUID uuid) {
        if (!settings.isTrackConsumption()) {
            return;
        }
        withSession(uuid, StatsRecord::incrementItemsConsumed);
    }

    private void withSession(UUID uuid, java.util.function.Consumer<StatsRecord> consumer) {
        PlayerSession session = sessions.get(uuid);
        if (session != null) {
            synchronized (session) {
                consumer.accept(session.getRecord());
            }
        }
    }

    /**
     * Writes a detached copy of a session's record plus its timeline row. A session whose initial
     * load failed retries the load first and is only written once it could be merged.
     */
    private void persist(PlayerSession session, StatsRecord copy) {
        if (!session.isLoaded() && !session.tryRecoverStoredRecord(storage, plugin)) {
            return;
        }
        StatsRecord toSave = copy;
        if (session.getStoredBase() != null) {
            toSave = session.getStoredBase().copy();
            toSave.addCountersFrom(copy);
        }
        save(toSave);
        StatsRecord timelineRecord = toSave;
        plugin.getTimelineService().ifPresent(t -> t.snapshot(timelineRecord));
    }

    private void save(StatsRecord record) {
        try {
            storage.save(record);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save stats for " + record.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Tracks a player's active session including session-start snapshot for delta calculations.
     * Every access to the record goes through the session's monitor because the autosave and the
     * web servers read it from other threads.
     */
    private static class PlayerSession {
        private volatile StatsRecord record;
        private StatsRecord sessionStartSnapshot;
        private long lastPlaytimeMark;
        private final long sessionStartTime;
        private volatile boolean loaded;
        // Stored record found after a failed initial load; the live record then only holds this session's progress.
        private volatile StatsRecord storedBase;

        PlayerSession(StatsRecord record, boolean loaded) {
            this.record = record;
            this.sessionStartSnapshot = record.copy();
            this.lastPlaytimeMark = System.currentTimeMillis();
            this.sessionStartTime = System.currentTimeMillis();
            this.loaded = loaded;
        }

        public StatsRecord getRecord() {
            return record;
        }

        /**
         * Returns the stats snapshot from when the session started.
         */
        public synchronized StatsRecord getSessionStartSnapshot() {
            return sessionStartSnapshot;
        }

        synchronized void resetSessionStart() {
            this.sessionStartSnapshot = record.copy();
        }

        /**
         * Returns the time when this session started.
         */
        public long getSessionStartTime() {
            return sessionStartTime;
        }

        boolean isLoaded() {
            return loaded;
        }

        StatsRecord getStoredBase() {
            return storedBase;
        }

        synchronized boolean tryRecoverStoredRecord(StatsStorage storage, SMPStats plugin) {
            try {
                StatsRecord stored = storage.loadOrCreate(record.getUuid(), record.getName());
                storedBase = stored.copy();
                sessionStartSnapshot = stored.copy();
                loaded = true;
                plugin.getLogger().info("Recovered stored stats for " + record.getName() + "; merging this session's progress.");
                return true;
            } catch (SQLException | RuntimeException e) {
                plugin.getLogger().warning("Stats for " + record.getName() + " still unavailable, not saving: " + e.getMessage());
                return false;
            }
        }

        /**
         * Turns a recovered session back into a plain one so edits apply to the full record.
         */
        synchronized void foldStoredBase() {
            if (storedBase == null) {
                return;
            }
            StatsRecord merged = storedBase.copy();
            merged.addCountersFrom(record);
            record = merged;
            storedBase = null;
        }

        synchronized void updatePlaytime(long now) {
            long delta = now - lastPlaytimeMark;
            if (delta > 0) {
                record.addPlaytimeMillis(delta);
                lastPlaytimeMark = now;
            }
        }

        synchronized StatsRecord snapshot() {
            StatsRecord copy = record.copy();
            long now = System.currentTimeMillis();
            long delta = now - lastPlaytimeMark;
            if (delta > 0) {
                copy.addPlaytimeMillis(delta);
            }
            if (storedBase != null) {
                StatsRecord merged = storedBase.copy();
                merged.addCountersFrom(copy);
                return merged;
            }
            return copy;
        }
    }
}
