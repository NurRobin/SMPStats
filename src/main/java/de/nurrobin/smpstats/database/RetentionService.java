package de.nurrobin.smpstats.database;

import de.nurrobin.smpstats.Settings;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/**
 * Periodically deletes raw event rows older than the configured retention.
 */
public class RetentionService {
    private static final long PERIOD_TICKS = 6L * 60 * 60 * 20;
    private static final long INITIAL_DELAY_TICKS = 60L * 20;

    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;
    private int taskId = -1;

    public RetentionService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::prune, INITIAL_DELAY_TICKS, PERIOD_TICKS).getTaskId();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public void prune() {
        Settings.RetentionSettings retention = settings.getRetentionSettings();
        long now = System.currentTimeMillis();
        try {
            int heatmap = retention.heatmapEventsDays() > 0
                    ? storage.deleteHeatmapEventsBefore(now - TimeUnit.DAYS.toMillis(retention.heatmapEventsDays())) : 0;
            int moments = retention.momentsDays() > 0
                    ? storage.deleteMomentsBefore(now - TimeUnit.DAYS.toMillis(retention.momentsDays())) : 0;
            int replays = retention.deathReplaysDays() > 0
                    ? storage.deleteDeathReplaysBefore(now - TimeUnit.DAYS.toMillis(retention.deathReplaysDays())) : 0;
            if (heatmap + moments + replays > 0) {
                plugin.getLogger().info("Retention removed " + heatmap + " heatmap events, " + moments
                        + " moments and " + replays + " death replays.");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Retention cleanup failed: " + e.getMessage());
        }
    }
}
