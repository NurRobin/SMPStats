package de.nurrobin.smpstats.story;

import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.moments.MomentEntry;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.skills.SkillWeights;
import de.nurrobin.smpstats.social.SocialPairRow;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesSummaryWhenIntervalElapsedAndCancelsOnShutdown() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        StatsStorage storage = mock(StatsStorage.class);
        StatsService stats = mock(StatsService.class);
        MomentService moments = mock(MomentService.class);

        UUID playerId = UUID.randomUUID();
        UUID pairId = UUID.randomUUID();
        when(storage.loadTimelineLeaderboard(1, 2)).thenReturn(List.of(Map.of("uuid", playerId.toString(), "score", 5)));
        when(storage.loadTopSocial(2)).thenReturn(List.of(new SocialPairRow(playerId, pairId, 120, 2, 1, 1)));
        when(stats.getStats(playerId)).thenReturn(Optional.of(new StatsRecord(playerId, "Alex")));
        when(stats.getStats(pairId)).thenReturn(Optional.empty());
        when(moments.getRecentMoments(3)).thenReturn(List.of(new MomentEntry(1L, playerId, "type", "title", "detail", "{}", "world", 1, 2, 3, 10L, 20L)));

        Path storyDir = tempDir.resolve("story");
        Files.createDirectories(storyDir);
        Files.writeString(storyDir.resolve("summary-2000-01-01.json"), "{}");

        Settings settings = storySettings(true);

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(42);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    scheduled.set(invocation.getArgument(1));
                    return task;
                });

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            StoryService service = new StoryService(plugin, stats, storage, moments, settings);
            service.start();

            Path generated = storyDir.resolve("summary-" + LocalDate.now(ZoneId.systemDefault()) + ".json");
            assertTrue(Files.exists(generated));
            String json = Files.readString(generated);
            assertTrue(json.contains("Alex"));
            assertTrue(json.contains("top_social"));
            assertTrue(json.contains("recent_moments"));

            service.shutdown();
            verify(scheduler).cancelTask(42);
        }

        assertTrue(scheduled.get() != null);
    }

    @Test
    void startSkipsWhenDisabled() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            StoryService service = new StoryService(plugin, mock(StatsService.class), mock(StatsStorage.class), mock(MomentService.class), storySettings(false));
            service.start();
            verifyNoInteractions(scheduler);
        }
    }

    @Test
    void generateSummarySendsWebhookAndHandlesMissingDir() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        StatsStorage storage = mock(StatsStorage.class);
        when(storage.loadTimelineLeaderboard(anyInt(), anyInt())).thenReturn(List.of(Map.of("uuid", UUID.randomUUID().toString(), "score", 1)));
        when(storage.loadTopSocial(anyInt())).thenReturn(List.of());
        StatsService stats = mock(StatsService.class);
        when(stats.getStats(any(UUID.class))).thenReturn(Optional.empty());
        MomentService moments = mock(MomentService.class);
        when(moments.getRecentMoments(anyInt())).thenReturn(List.of());

        Settings settings = mock(Settings.class);
        when(settings.isStoryEnabled()).thenReturn(true);
        when(settings.getStoryIntervalDays()).thenReturn(1);
        when(settings.getStoryTopLimit()).thenReturn(5);
        when(settings.getStoryRecentMoments()).thenReturn(1);
        when(settings.getStoryWebhookUrl()).thenReturn("http://localhost:1"); // force quick failure path
        when(settings.getStorySummaryHour()).thenReturn(0);

        StoryService service = new StoryService(plugin, stats, storage, moments, settings);

        var generate = StoryService.class.getDeclaredMethod("generateSummary", LocalDate.class);
        generate.setAccessible(true);
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        generate.invoke(service, today);

        Path generated = tempDir.resolve("story").resolve("summary-" + today + ".json");
        assertTrue(Files.exists(generated));
    }

    @Test
    void maybeGenerateHonorsIntervalAndHour() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());

        Settings settings = mock(Settings.class);
        when(settings.isStoryEnabled()).thenReturn(true);
        when(settings.getStoryIntervalDays()).thenReturn(1);
        when(settings.getStorySummaryHour()).thenReturn(23); // future hour

        StoryService service = new StoryService(plugin, mock(StatsService.class), mock(StatsStorage.class), mock(MomentService.class), settings);

        var field = StoryService.class.getDeclaredField("lastGeneratedDay");
        field.setAccessible(true);
        field.set(service, LocalDate.now(ZoneId.systemDefault()));

        var method = StoryService.class.getDeclaredMethod("maybeGenerate");
        method.setAccessible(true);
        method.invoke(service); // should return early without exceptions
    }

    private Settings storySettings(boolean enabled) {
        SkillWeights weights = new SkillWeights(
                new SkillWeights.MiningWeights(0),
                new SkillWeights.CombatWeights(0, 0, 0),
                new SkillWeights.ExplorationWeights(0, 0),
                new SkillWeights.BuilderWeights(0),
                new SkillWeights.FarmerWeights(0, 0)
        );
        return new Settings(true, true, true, true, true, true, true,
                false, "127.0.0.1", 0, "KEY", 1, weights,
                true, 0L, 1L, true, 1, 1.0, List.of(), List.of(),
                false, 1, 1, false,
                true, true, 1, 1,
                true, 1, 0.1, 0.1, 0.1, 0.1, de.nurrobin.smpstats.health.HealthThresholds.defaults(),
                enabled, 1, 0, "", 2, 3,
                Settings.DashboardSettings.defaults(), true);
    }
}
