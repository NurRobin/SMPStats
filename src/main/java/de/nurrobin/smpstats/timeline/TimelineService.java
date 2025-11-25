package de.nurrobin.smpstats.timeline;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;

public class TimelineService {
    private final Plugin plugin;
    private final StatsStorage storage;
    private Settings settings;

    public TimelineService(Plugin plugin, StatsStorage storage, Settings settings) {
        this.plugin = plugin;
        this.storage = storage;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void snapshot(StatsRecord record) {
        if (!settings.isTimelineEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        try {
            storage.upsertTimeline(record, today);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not upsert timeline for " + record.getName() + ": " + e.getMessage());
        }
    }
}
