package de.nurrobin.smpstats.timeline;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class TimelineServiceTest {

    @Test
    void skipsSnapshotWhenDisabled() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isTimelineEnabled()).thenReturn(false);

        TimelineService service = new TimelineService(plugin, storage, settings);
        service.snapshot(new StatsRecord(UUID.randomUUID(), "Alex"));
        verify(storage, never()).upsertTimeline(any(), any());
    }

    @Test
    void upsertsTimelineWhenEnabled() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isTimelineEnabled()).thenReturn(true);

        TimelineService service = new TimelineService(plugin, storage, settings);
        StatsRecord record = new StatsRecord(UUID.randomUUID(), "Alex");
        service.snapshot(record);
        verify(storage).upsertTimeline(any(), any());
    }

    @Test
    void logsWarningWhenStorageFails() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        doThrow(new java.sql.SQLException("fail")).when(storage).upsertTimeline(any(), any());
        Settings settings = mock(Settings.class);
        when(settings.isTimelineEnabled()).thenReturn(true);

        TimelineService service = new TimelineService(plugin, storage, settings);
        service.snapshot(new StatsRecord(UUID.randomUUID(), "Alex"));

        verify(storage).upsertTimeline(any(), any());
    }
}
