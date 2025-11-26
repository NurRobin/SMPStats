package de.nurrobin.smpstats.story;

import com.google.gson.Gson;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.social.SocialPairRow;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StoryService {
    private final Plugin plugin;
    private final StatsService statsService;
    private final StatsStorage storage;
    private final MomentService momentService;
    private Settings settings;
    private final Gson gson = new Gson();
    private int taskId = -1;
    private LocalDate lastGeneratedDay;

    public StoryService(Plugin plugin, StatsService statsService, StatsStorage storage, MomentService momentService, Settings settings) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.storage = storage;
        this.momentService = momentService;
        this.settings = settings;
    }

    public void updateSettings(Settings settings) {
        this.settings = settings;
    }

    public void start() {
        if (!settings.isStoryEnabled()) {
            return;
        }
        lastGeneratedDay = loadLastGeneratedDay();
        if (lastGeneratedDay == null) {
            lastGeneratedDay = LocalDate.now(ZoneId.systemDefault());
        }
        long periodTicks = 60L * 60L * 20L; // hourly check
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::maybeGenerate, 100L, periodTicks).getTaskId();
        maybeGenerate();
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void maybeGenerate() {
        if (!settings.isStoryEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (lastGeneratedDay != null && !lastGeneratedDay.isBefore(today.minusDays(settings.getStoryIntervalDays() - 1L))) {
            return;
        }
        if (LocalTime.now(ZoneId.systemDefault()).getHour() < settings.getStorySummaryHour()) {
            return;
        }
        try {
            generateSummary(today);
            lastGeneratedDay = today;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not generate story summary: " + e.getMessage());
        }
    }

    private void generateSummary(LocalDate today) throws Exception {
        int days = settings.getStoryIntervalDays();
        int topLimit = settings.getStoryTopLimit();
        List<Map<String, Object>> leaderboard = storage.loadTimelineLeaderboard(days, topLimit);
        List<Map<String, Object>> topPlayers = new ArrayList<>();
        for (Map<String, Object> row : leaderboard) {
            UUID uuid = UUID.fromString(row.get("uuid").toString());
            Map<String, Object> entry = new LinkedHashMap<>(row);
            entry.put("name", resolveName(uuid));
            topPlayers.add(entry);
        }

        List<Map<String, Object>> social = new ArrayList<>();
        for (SocialPairRow pair : storage.loadTopSocial(topLimit)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("a", pair.uuidA().toString());
            map.put("b", pair.uuidB().toString());
            map.put("name_a", resolveName(pair.uuidA()));
            map.put("name_b", resolveName(pair.uuidB()));
            map.put("seconds", pair.seconds());
            map.put("shared_kills", pair.sharedKills());
            map.put("shared_player_kills", pair.sharedPlayerKills());
            map.put("shared_mob_kills", pair.sharedMobKills());
            social.add(map);
        }

        List<?> moments = momentService.getRecentMoments(settings.getStoryRecentMoments());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generated_at", Instant.now().toString());
        summary.put("range_days", days);
        summary.put("top_players", topPlayers);
        summary.put("top_social", social);
        summary.put("recent_moments", moments);

        Path outDir = plugin.getDataFolder().toPath().resolve("story");
        Files.createDirectories(outDir);
        Path file = outDir.resolve("summary-" + today + ".json");
        Files.writeString(file, gson.toJson(summary), StandardCharsets.UTF_8);
        if (settings.getStoryWebhookUrl() != null && !settings.getStoryWebhookUrl().isBlank()) {
            sendWebhook(summary, settings.getStoryWebhookUrl());
        }
        plugin.getLogger().info("Story summary written to " + file.getFileName());
        lastGeneratedDay = today;
    }

    private String resolveName(UUID uuid) {
        return statsService.getStats(uuid)
                .map(de.nurrobin.smpstats.StatsRecord::getName)
                .orElse(uuid.toString());
    }

    private void sendWebhook(Map<String, Object> payload, String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            byte[] data = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }
            conn.getResponseCode(); // trigger send
            conn.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("Story webhook failed: " + e.getMessage());
        }
    }

    private LocalDate loadLastGeneratedDay() {
        try {
            Path dir = plugin.getDataFolder().toPath().resolve("story");
            if (!Files.exists(dir)) {
                return null;
            }
            LocalDate latest = null;
            try (var stream = Files.list(dir)) {
                for (Path p : stream.toList()) {
                    String name = p.getFileName().toString();
                    if (!name.startsWith("summary-") || !name.endsWith(".json")) {
                        continue;
                    }
                    String datePart = name.substring("summary-".length(), name.length() - ".json".length());
                    try {
                        LocalDate parsed = LocalDate.parse(datePart);
                        if (latest == null || parsed.isAfter(latest)) {
                            latest = parsed;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return latest;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not load last story date: " + e.getMessage());
            return null;
        }
    }
}
