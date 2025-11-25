package de.nurrobin.smpstats.moments;

import com.google.gson.Gson;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MomentService {
    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;
    private final Map<UUID, ActiveWindow> windows = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private int flushTaskId = -1;

    public MomentService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        if (!settings.isMomentsEnabled()) {
            return;
        }
        long periodTicks = Math.max(1, settings.getMomentsFlushSeconds()) * 20L;
        flushTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushStale, periodTicks, periodTicks).getTaskId();
    }

    public void shutdown() {
        if (flushTaskId != -1) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        flushAll();
    }

    public void onDiamondFound(Player player, Location location) {
        if (!settings.isMomentsEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        ActiveWindow current = windows.get(player.getUniqueId());
        long windowMillis = settings.getDiamondWindowSeconds() * 1000L;
        if (current == null || current.type != MomentType.DIAMOND_FIND || (now - current.lastUpdate) > windowMillis) {
            // close old window if present
            flushWindow(player.getUniqueId(), current);
            current = new ActiveWindow(MomentType.DIAMOND_FIND, now, location);
            windows.put(player.getUniqueId(), current);
        }
        current.count++;
        current.lastUpdate = now;
        current.lastLocation = location;
    }

    public void onFirstDeath(Player player, Location location) {
        if (!settings.isMomentsEnabled()) {
            return;
        }
        // Only first death: check existing moments for this type? For simplicity store once per session using window map
        UUID uuid = player.getUniqueId();
        String key = "first-death-" + uuid;
        if (windows.containsKey(uuid) && windows.get(uuid).metadata != null && key.equals(windows.get(uuid).metadata)) {
            return;
        }
        saveMoment(MomentEntry.fromLocation(uuid, MomentType.FIRST_DEATH, "Erster Tod", player.getName() + " ist das erste Mal gestorben.", null, location, System.currentTimeMillis(), System.currentTimeMillis()));
        ActiveWindow marker = new ActiveWindow(MomentType.FIRST_DEATH, System.currentTimeMillis(), location);
        marker.metadata = key;
        windows.put(uuid, marker);
    }

    public void onBigFallDeath(Player player, Location location, double fallDistance) {
        if (!settings.isMomentsEnabled()) {
            return;
        }
        String detail = String.format("%s fiel aus %.1f Blöcken Höhe.", player.getName(), fallDistance);
        saveMoment(MomentEntry.fromLocation(player.getUniqueId(), MomentType.BIG_FALL_DEATH, "Big Fall Death", detail, null, location, System.currentTimeMillis(), System.currentTimeMillis()));
    }

    public List<MomentEntry> getRecentMoments(int limit) {
        try {
            return storage.loadRecentMoments(limit);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load recent moments: " + e.getMessage());
            return List.of();
        }
    }

    private void flushStale() {
        long now = System.currentTimeMillis();
        long windowMillis = settings.getDiamondWindowSeconds() * 1000L;
        for (Map.Entry<UUID, ActiveWindow> entry : windows.entrySet()) {
            ActiveWindow window = entry.getValue();
            if (window.type == MomentType.DIAMOND_FIND && (now - window.lastUpdate) > windowMillis) {
                flushWindow(entry.getKey(), window);
            }
        }
    }

    private void flushAll() {
        for (Map.Entry<UUID, ActiveWindow> entry : windows.entrySet()) {
            flushWindow(entry.getKey(), entry.getValue());
        }
        windows.clear();
    }

    private void flushWindow(UUID playerId, ActiveWindow window) {
        if (window == null) {
            return;
        }
        if (window.type == MomentType.DIAMOND_FIND && window.count > 0 && window.origin != null) {
            String detail = "Diamanten gefunden: " + window.count;
            String payload = gson.toJson(Map.of("count", window.count));
            saveMoment(MomentEntry.fromLocation(playerId, MomentType.DIAMOND_FIND, "Diamanten Run", detail, payload, window.origin, window.startedAt, window.lastUpdate));
        }
        windows.remove(playerId);
    }

    private void saveMoment(MomentEntry entry) {
        try {
            storage.saveMoment(entry);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save moment: " + e.getMessage());
        }
    }

    private static class ActiveWindow {
        private final MomentType type;
        private final long startedAt;
        private long lastUpdate;
        private int count;
        private Location origin;
        private Location lastLocation;
        private String metadata;

        ActiveWindow(MomentType type, long startedAt, Location origin) {
            this.type = type;
            this.startedAt = startedAt;
            this.lastUpdate = startedAt;
            this.origin = origin;
            this.count = 0;
        }
    }
}
