package de.nurrobin.smpstats.database;

import de.nurrobin.smpstats.Settings;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionServiceTest {

    @Test
    void prunesOnlyTablesWithPositiveRetention() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.getRetentionSettings()).thenReturn(new Settings.RetentionSettings(30, 0, 10));

        long before = System.currentTimeMillis();
        new RetentionService(plugin, storage, settings).prune();
        long after = System.currentTimeMillis();

        long thirtyDays = TimeUnit.DAYS.toMillis(30);
        long tenDays = TimeUnit.DAYS.toMillis(10);
        verify(storage).deleteHeatmapEventsBefore(longThat(ts -> ts >= before - thirtyDays && ts <= after - thirtyDays));
        verify(storage, never()).deleteMomentsBefore(anyLong());
        verify(storage).deleteDeathReplaysBefore(longThat(ts -> ts >= before - tenDays && ts <= after - tenDays));
    }
}
