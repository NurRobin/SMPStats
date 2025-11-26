package de.nurrobin.smpstats.social;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.database.StatsStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SocialStatsServiceTest {

    @Test
    void recordsNearbyPairsDuringSample() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isSocialEnabled()).thenReturn(true);
        when(settings.getSocialSampleSeconds()).thenReturn(5);
        when(settings.getSocialNearbyRadius()).thenReturn(10);

        World world = mock(World.class);
        Location locA = new Location(world, 0, 64, 0);
        Location locB = new Location(world, 3, 64, 4);

        Player a = mock(Player.class);
        Player b = mock(Player.class);
        when(a.getUniqueId()).thenReturn(UUID.randomUUID());
        when(b.getUniqueId()).thenReturn(UUID.randomUUID());
        when(a.getWorld()).thenReturn(world);
        when(b.getWorld()).thenReturn(world);
        when(a.getLocation()).thenReturn(locA);
        when(b.getLocation()).thenReturn(locB);
        when(world.getPlayers()).thenReturn(List.of(a, b));

        SocialStatsService service = new SocialStatsService(plugin, storage, settings);

        try (var mocked = mockStatic(Bukkit.class)) {
            mocked.when(Bukkit::getOnlinePlayers).thenReturn((java.util.Collection) List.of(a, b));

            // invoke private sample method to avoid scheduler complexity
            var sample = SocialStatsService.class.getDeclaredMethod("sample");
            sample.setAccessible(true);
            sample.invoke(service);
        }

        verify(storage).incrementSocialPair(any(UUID.class), any(UUID.class), eq(5L), eq(0L), eq(0L), eq(0L));
    }

    @Test
    void recordsSharedKillsForNearbyPlayers() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isSocialEnabled()).thenReturn(true);
        when(settings.getSocialNearbyRadius()).thenReturn(5);

        World world = mock(World.class);
        Location killerLoc = new Location(world, 0, 64, 0);
        Location otherLoc = new Location(world, 1, 64, 1);

        Player killer = mock(Player.class);
        Player other = mock(Player.class);
        when(killer.getUniqueId()).thenReturn(UUID.randomUUID());
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        when(killer.getWorld()).thenReturn(world);
        when(other.getWorld()).thenReturn(world);
        when(killer.getLocation()).thenReturn(killerLoc);
        when(other.getLocation()).thenReturn(otherLoc);
        when(world.getPlayers()).thenReturn(List.of(killer, other));

        SocialStatsService service = new SocialStatsService(plugin, storage, settings);
        service.recordSharedKill(killer, true);

        verify(storage).incrementSocialPair(any(UUID.class), any(UUID.class), eq(0L), eq(1L), eq(1L), eq(0L));
    }

    @Test
    void startAndShutdownScheduleAndCancelTask() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        StatsStorage storage = mock(StatsStorage.class);
        Settings settings = mock(Settings.class);
        when(settings.isSocialEnabled()).thenReturn(true);
        when(settings.getSocialSampleSeconds()).thenReturn(2);
        when(settings.getSocialNearbyRadius()).thenReturn(5);

        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        var task = mock(org.bukkit.scheduler.BukkitTask.class);
        when(task.getTaskId()).thenReturn(42);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

        SocialStatsService service = new SocialStatsService(plugin, storage, settings);
        try (var mocked = mockStatic(Bukkit.class)) {
            mocked.when(Bukkit::getScheduler).thenReturn(scheduler);

            service.start();
            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong());

            service.shutdown();
            verify(scheduler).cancelTask(42);
        }

    }
}
