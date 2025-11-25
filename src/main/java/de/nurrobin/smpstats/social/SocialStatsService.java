package de.nurrobin.smpstats.social;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SocialStatsService {
    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;
    private final Map<PairKey, Long> secondsTogether = new ConcurrentHashMap<>();
    private int taskId = -1;

    public SocialStatsService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        if (!settings.isSocialEnabled()) return;
        long periodTicks = Math.max(1, settings.getSocialSampleSeconds()) * 20L;
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, periodTicks, periodTicks).getTaskId();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        flush();
    }

    private void sample() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int n = players.size();
        for (int i = 0; i < n; i++) {
            Player a = players.get(i);
            for (int j = i + 1; j < n; j++) {
                Player b = players.get(j);
                if (isNearby(a, b)) {
                    PairKey key = PairKey.of(a.getUniqueId(), b.getUniqueId());
                    secondsTogether.merge(key, (long) settings.getSocialSampleSeconds(), Long::sum);
                }
            }
        }
        flush();
    }

    private boolean isNearby(Player a, Player b) {
        if (!a.getWorld().equals(b.getWorld())) return false;
        return a.getLocation().distanceSquared(b.getLocation()) <= 16 * 16;
    }

    public Map<PairKey, Long> getLivePairs() {
        return Collections.unmodifiableMap(secondsTogether);
    }

    private void flush() {
        if (secondsTogether.isEmpty()) return;
        List<Map.Entry<PairKey, Long>> batch = new ArrayList<>(secondsTogether.entrySet());
        secondsTogether.clear();
        for (Map.Entry<PairKey, Long> entry : batch) {
            try {
                storage.incrementSocialPair(entry.getKey().a, entry.getKey().b, entry.getValue());
            } catch (Exception e) {
                plugin.getLogger().warning("Could not persist social pair: " + e.getMessage());
            }
        }
    }

    public record PairKey(UUID a, UUID b) {
        public static PairKey of(UUID a, UUID b) {
            if (a.compareTo(b) <= 0) return new PairKey(a, b);
            return new PairKey(b, a);
        }
    }
}
