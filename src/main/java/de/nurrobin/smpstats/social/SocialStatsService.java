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
    private final Map<PairKey, KillTally> sharedKills = new ConcurrentHashMap<>();
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
        int radius = Math.max(1, settings.getSocialNearbyRadius());
        int r2 = radius * radius;
        return a.getLocation().distanceSquared(b.getLocation()) <= r2;
    }

    public Map<PairKey, Long> getLivePairs() {
        return Collections.unmodifiableMap(secondsTogether);
    }

    private void flush() {
        List<Map.Entry<PairKey, Long>> secondsBatch = new ArrayList<>(secondsTogether.entrySet());
        secondsTogether.clear();
        List<Map.Entry<PairKey, KillTally>> killBatch = new ArrayList<>(sharedKills.entrySet());
        sharedKills.clear();
        for (Map.Entry<PairKey, Long> entry : secondsBatch) {
            try {
                storage.incrementSocialPair(entry.getKey().a, entry.getKey().b, entry.getValue(), 0, 0, 0);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not persist social pair: " + e.getMessage());
            }
        }
        for (Map.Entry<PairKey, KillTally> entry : killBatch) {
            KillTally tally = entry.getValue();
            try {
                storage.incrementSocialPair(entry.getKey().a, entry.getKey().b, 0, tally.total, tally.playerKills, tally.mobKills);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not persist shared kill pair: " + e.getMessage());
            }
        }
    }

    public record PairKey(UUID a, UUID b) {
        public static PairKey of(UUID a, UUID b) {
            if (a.compareTo(b) <= 0) return new PairKey(a, b);
            return new PairKey(b, a);
        }
    }

    public void recordSharedKill(Player killer, boolean playerKill) {
        if (!settings.isSocialEnabled()) {
            return;
        }
        List<Player> worldPlayers = killer.getWorld().getPlayers();
        int radius = Math.max(1, settings.getSocialNearbyRadius());
        double maxDistanceSquared = radius * radius;
        for (Player other : worldPlayers) {
            if (other.getUniqueId().equals(killer.getUniqueId())) {
                continue;
            }
            if (other.getLocation().distanceSquared(killer.getLocation()) > maxDistanceSquared) {
                continue;
            }
            PairKey key = PairKey.of(killer.getUniqueId(), other.getUniqueId());
            sharedKills.compute(key, (k, existing) -> {
                KillTally tally = existing != null ? existing : new KillTally();
                tally.total++;
                if (playerKill) {
                    tally.playerKills++;
                } else {
                    tally.mobKills++;
                }
                return tally;
            });
        }
        flush();
    }

    private static class KillTally {
        long total;
        long playerKills;
        long mobKills;
    }
}
