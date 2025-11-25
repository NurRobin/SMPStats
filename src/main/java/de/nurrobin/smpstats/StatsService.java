package de.nurrobin.smpstats;

import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.World;
import org.bukkit.entity.Player;

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
    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();

    public StatsService(SMPStats plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        StatsRecord record;
        try {
            record = storage.loadOrCreate(uuid, player.getName());
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load stats for " + player.getName() + ": " + e.getMessage());
            record = new StatsRecord(uuid, player.getName());
            long now = System.currentTimeMillis();
            record.setFirstJoin(now);
            record.setLastJoin(now);
        }

        record.setLastJoin(System.currentTimeMillis());
        sessions.put(uuid, new PlayerSession(record));
    }

    public void handleQuit(Player player) {
        PlayerSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        session.updatePlaytime(now);
        session.getRecord().setLastJoin(now);
        save(session.getRecord());
    }

    public void flushOnline() {
        long now = System.currentTimeMillis();
        for (PlayerSession session : sessions.values()) {
            session.updatePlaytime(now);
            save(session.getRecord());
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
            consumer.accept(session.getRecord());
        }
    }

    private void save(StatsRecord record) {
        try {
            storage.save(record);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save stats for " + record.getName() + ": " + e.getMessage());
        }
    }

    private static class PlayerSession {
        private final StatsRecord record;
        private long lastPlaytimeMark;

        PlayerSession(StatsRecord record) {
            this.record = record;
            this.lastPlaytimeMark = System.currentTimeMillis();
        }

        public StatsRecord getRecord() {
            return record;
        }

        void updatePlaytime(long now) {
            long delta = now - lastPlaytimeMark;
            if (delta > 0) {
                record.addPlaytimeMillis(delta);
                lastPlaytimeMark = now;
            }
        }

        StatsRecord snapshot() {
            StatsRecord copy = record.copy();
            long now = System.currentTimeMillis();
            long delta = now - lastPlaytimeMark;
            if (delta > 0) {
                copy.addPlaytimeMillis(delta);
            }
            return copy;
        }
    }
}
