package de.nurrobin.smpstats;

import de.nurrobin.smpstats.api.ApiServer;
import de.nurrobin.smpstats.commands.StatsCommand;
import de.nurrobin.smpstats.commands.SStatsCommand;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.listeners.BlockListener;
import de.nurrobin.smpstats.listeners.CombatListener;
import de.nurrobin.smpstats.listeners.CraftingListener;
import de.nurrobin.smpstats.listeners.JoinQuitListener;
import de.nurrobin.smpstats.listeners.MovementListener;
import de.nurrobin.smpstats.listeners.MomentListener;
import de.nurrobin.smpstats.listeners.HeatmapListener;
import de.nurrobin.smpstats.skills.SkillWeights;
import de.nurrobin.smpstats.moments.MomentService;
import de.nurrobin.smpstats.heatmap.HeatmapService;
import de.nurrobin.smpstats.moments.MomentConfigParser;
import de.nurrobin.smpstats.heatmap.HotspotDefinition;
import de.nurrobin.smpstats.social.SocialStatsService;
import de.nurrobin.smpstats.timeline.TimelineService;
import de.nurrobin.smpstats.timeline.DeathReplayService;
import de.nurrobin.smpstats.health.ServerHealthService;
import de.nurrobin.smpstats.story.StoryService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Objects;

public class SMPStats extends JavaPlugin {
    private static final int CONFIG_VERSION = 3;
    private StatsStorage storage;
    private StatsService statsService;
    private Settings settings;
    private ApiServer apiServer;
    private MomentService momentService;
    private HeatmapService heatmapService;
    private SocialStatsService socialStatsService;
    private TimelineService timelineService;
    private DeathReplayService deathReplayService;
    private ServerHealthService serverHealthService;
    private StoryService storyService;
    private int autosaveTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigVersion();
        this.settings = loadSettings();

        try {
            this.storage = new StatsStorage(this);
            storage.init();
        } catch (SQLException | IOException e) {
            getLogger().severe("Could not start SQLite database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.statsService = new StatsService(this, storage, settings);
        this.momentService = new MomentService(this, storage, settings);
        this.heatmapService = new HeatmapService(this, storage, settings);
        this.socialStatsService = new SocialStatsService(this, storage, settings);
        this.timelineService = new TimelineService(this, storage, settings);
        this.deathReplayService = new DeathReplayService(this, storage, settings);
        this.serverHealthService = new ServerHealthService(this, settings);
        this.storyService = new StoryService(this, statsService, storage, momentService, settings);

        registerListeners();
        registerCommands();
        for (Player online : Bukkit.getOnlinePlayers()) {
            statsService.handleJoin(online);
        }
        startAutosave();
        momentService.start();
        heatmapService.start();
        socialStatsService.start();
        if (deathReplayService != null) {
            deathReplayService.start();
        }
        if (serverHealthService != null) {
            serverHealthService.start();
        }
        if (storyService != null) {
            storyService.start();
        }
        startApiServer();
        logStartupBanner("Aktiv");
    }

    @Override
    public void onDisable() {
        cancelAutosave();
        if (statsService != null) {
            statsService.shutdown();
        }
        if (momentService != null) {
            momentService.shutdown();
        }
        if (heatmapService != null) {
            heatmapService.shutdown();
        }
        if (socialStatsService != null) {
            socialStatsService.shutdown();
        }
        if (deathReplayService != null) {
            deathReplayService.shutdown();
        }
        if (serverHealthService != null) {
            serverHealthService.shutdown();
        }
        if (storyService != null) {
            storyService.shutdown();
        }
        if (apiServer != null) {
            apiServer.stop();
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (IOException e) {
                getLogger().warning("Failed to close storage: " + e.getMessage());
            }
        }
    }

    public Settings getSettings() {
        return settings;
    }

    public java.util.Optional<TimelineService> getTimelineService() {
        return java.util.Optional.ofNullable(timelineService);
    }

    public java.util.Optional<DeathReplayService> getDeathReplayService() {
        return java.util.Optional.ofNullable(deathReplayService);
    }

    public java.util.Optional<ServerHealthService> getServerHealthService() {
        return java.util.Optional.ofNullable(serverHealthService);
    }

    public java.util.Optional<StoryService> getStoryService() {
        return java.util.Optional.ofNullable(storyService);
    }

    public void reloadPluginConfig(CommandSender sender) {
        reloadConfig();
        ensureConfigVersion();
        this.settings = loadSettings();
        statsService.updateSettings(settings);
        if (momentService != null) {
            momentService.shutdown();
            momentService.updateSettings(settings);
            momentService.start();
        }
        if (heatmapService != null) {
            heatmapService.shutdown();
            heatmapService.updateSettings(settings);
            heatmapService.start();
        }
        if (socialStatsService != null) {
            socialStatsService.shutdown();
            socialStatsService.updateSettings(settings);
            socialStatsService.start();
        }
        if (timelineService != null) {
            timelineService.updateSettings(settings);
        }
        if (deathReplayService != null) {
            deathReplayService.shutdown();
            deathReplayService.updateSettings(settings);
            deathReplayService.start();
        }
        if (serverHealthService != null) {
            serverHealthService.shutdown();
            serverHealthService.updateSettings(settings);
            serverHealthService.start();
        }
        if (storyService != null) {
            storyService.shutdown();
            storyService.updateSettings(settings);
            storyService.start();
        }
        startAutosave();
        restartApiServer();
        logStartupBanner("Reload");
        sender.sendMessage(ChatColor.GREEN + "SMPStats Konfiguration neu geladen.");
    }

    private Settings loadSettings() {
        FileConfiguration config = getConfig();
        boolean movement = config.getBoolean("tracking.movement", true);
        boolean blocks = config.getBoolean("tracking.blocks", true);
        boolean kills = config.getBoolean("tracking.kills", true);
        boolean biomes = config.getBoolean("tracking.biomes", true);
        boolean crafting = config.getBoolean("tracking.crafting", true);
        boolean damage = config.getBoolean("tracking.damage", true);
        boolean consumption = config.getBoolean("tracking.consumption", true);

        boolean apiEnabled = config.getBoolean("api.enabled", false);
        int apiPort = config.getInt("api.port", 8765);
        String apiKey = config.getString("api.api_key", "CHANGEME123");
        int autosaveMinutes = Math.max(1, config.getInt("autosave_minutes", 5));

        SkillWeights skillWeights = new SkillWeights(
                new SkillWeights.MiningWeights(config.getDouble("skills.mining.blocks_broken_weight", 0.05)),
                new SkillWeights.CombatWeights(
                        config.getDouble("skills.combat.player_kill_weight", 5.0),
                        config.getDouble("skills.combat.mob_kill_weight", 2.0),
                        config.getDouble("skills.combat.damage_dealt_weight", 0.02)
                ),
                new SkillWeights.ExplorationWeights(
                        config.getDouble("skills.exploration.distance_weight", 0.01),
                        config.getDouble("skills.exploration.biomes_weight", 8.0)
                ),
                new SkillWeights.BuilderWeights(
                        config.getDouble("skills.builder.blocks_placed_weight", 0.05)
                ),
                new SkillWeights.FarmerWeights(
                        config.getDouble("skills.farmer.items_crafted_weight", 0.02),
                        config.getDouble("skills.farmer.items_consumed_weight", 0.01)
                )
        );

        boolean momentsEnabled = config.getBoolean("moments.enabled", true);
        long diamondWindowSeconds = config.getLong("moments.diamond_window_seconds", 30L);
        long momentsFlushSeconds = config.getLong("moments.flush_seconds", 10L);

        boolean heatmapEnabled = config.getBoolean("heatmap.enabled", true);
        int heatmapFlushMinutes = Math.max(1, config.getInt("heatmap.flush_minutes", 5));

        MomentConfigParser parser = new MomentConfigParser();
        java.util.List<de.nurrobin.smpstats.moments.MomentDefinition> momentDefinitions = parser.parse(config.getConfigurationSection("moments"));
        java.util.List<HotspotDefinition> hotspots = parseHotspots(config.getConfigurationSection("heatmap.hotspots"));

        boolean socialEnabled = config.getBoolean("social.enabled", true);
        int socialSampleSeconds = Math.max(1, config.getInt("social.sample_seconds", 5));
        int socialNearbyRadius = Math.max(1, config.getInt("social.nearby_radius", 16));

        boolean timelineEnabled = config.getBoolean("timeline.enabled", true);

        boolean deathReplayEnabled = config.getBoolean("death_replay.enabled", true);
        boolean deathReplayInventoryItems = config.getBoolean("death_replay.include_inventory_items", true);
        int deathReplayNearbyRadius = Math.max(1, config.getInt("death_replay.nearby_radius", 16));
        int deathReplayLimit = Math.max(1, config.getInt("death_replay.limit", 20));

        boolean healthEnabled = config.getBoolean("health.enabled", true);
        int healthSampleMinutes = Math.max(1, config.getInt("health.sample_minutes", 5));
        double healthChunkWeight = config.getDouble("health.weights.chunk", 0.02);
        double healthEntityWeight = config.getDouble("health.weights.entity", 0.005);
        double healthHopperWeight = config.getDouble("health.weights.hopper", 0.2);
        double healthRedstoneWeight = config.getDouble("health.weights.redstone", 0.1);

        boolean storyEnabled = config.getBoolean("story.enabled", true);
        int storyIntervalDays = Math.max(1, config.getInt("story.interval_days", 7));
        int storySummaryHour = Math.min(23, Math.max(0, config.getInt("story.summary_hour", 6)));
        String storyWebhookUrl = config.getString("story.webhook_url", "");
        int storyTopLimit = Math.max(1, config.getInt("story.top_limit", 5));
        int storyRecentMoments = Math.max(0, config.getInt("story.recent_moments", 10));

        return new Settings(movement, blocks, kills, biomes, crafting, damage, consumption,
                apiEnabled, apiPort, apiKey, autosaveMinutes, skillWeights,
                momentsEnabled, diamondWindowSeconds, momentsFlushSeconds, heatmapEnabled, heatmapFlushMinutes, momentDefinitions, hotspots,
                socialEnabled, socialSampleSeconds, socialNearbyRadius, timelineEnabled,
                deathReplayEnabled, deathReplayInventoryItems, deathReplayNearbyRadius, deathReplayLimit,
                healthEnabled, healthSampleMinutes, healthChunkWeight, healthEntityWeight, healthHopperWeight, healthRedstoneWeight,
                storyEnabled, storyIntervalDays, storySummaryHour, storyWebhookUrl, storyTopLimit, storyRecentMoments);
    }

    private void ensureConfigVersion() {
        FileConfiguration config = getConfig();
        int current = config.getInt("config_version", 1);
        if (current > CONFIG_VERSION) {
            getLogger().warning("Config version (" + current + ") is newer than expected (" + CONFIG_VERSION + ").");
            return;
        }
        if (current < CONFIG_VERSION) {
            getLogger().info("Updating config.yml from version " + current + " to " + CONFIG_VERSION + " ...");
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(Objects.requireNonNull(getResource("config.yml")), java.nio.charset.StandardCharsets.UTF_8));
            for (String key : defaults.getKeys(true)) {
                if (!config.isSet(key)) {
                    config.set(key, defaults.get(key));
                }
            }
            config.set("config_version", CONFIG_VERSION);
            saveConfig();
        }
    }

    private java.util.List<HotspotDefinition> parseHotspots(org.bukkit.configuration.ConfigurationSection section) {
        java.util.List<HotspotDefinition> list = new java.util.ArrayList<>();
        if (section == null) {
            return list;
        }
        var hotspotSection = section.getConfigurationSection("heatmap.hotspots");
        if (hotspotSection == null && section.get("heatmap.hotspots") instanceof java.util.List<?>) {
            // If top-level section passed already contains hotspots list
            hotspotSection = section.getConfigurationSection("hotspots");
        }
        if (hotspotSection == null) {
            return list;
        }
        for (String key : hotspotSection.getKeys(false)) {
            var cfg = hotspotSection.getConfigurationSection(key);
            if (cfg == null) continue;
            String name = cfg.getString("name", key);
            String world = cfg.getString("world", "world");
            int minX = cfg.getInt("min.x", cfg.getInt("minX", 0));
            int minZ = cfg.getInt("min.z", cfg.getInt("minZ", 0));
            int maxX = cfg.getInt("max.x", cfg.getInt("maxX", 0));
            int maxZ = cfg.getInt("max.z", cfg.getInt("maxZ", 0));
            list.add(new HotspotDefinition(name, world, minX, minZ, maxX, maxZ));
        }
        return list;
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new JoinQuitListener(statsService), this);
        pm.registerEvents(new BlockListener(this, statsService), this);
        pm.registerEvents(new CombatListener(this, statsService, socialStatsService), this);
        pm.registerEvents(new MovementListener(this, statsService), this);
        pm.registerEvents(new CraftingListener(this, statsService), this);
        pm.registerEvents(new MomentListener(momentService, deathReplayService), this);
        pm.registerEvents(new HeatmapListener(heatmapService), this);
    }

    private void registerCommands() {
        StatsCommand statsCommand = new StatsCommand(this, statsService);
        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(statsCommand);
            getCommand("stats").setTabCompleter(statsCommand);
        } else {
            getLogger().warning("Command /stats is missing from plugin.yml");
        }
        SStatsCommand adminCommand = new SStatsCommand(this, statsService);
        if (getCommand("smpstats") != null) {
            getCommand("smpstats").setExecutor(adminCommand);
            getCommand("smpstats").setTabCompleter(adminCommand);
        }
        if (getCommand("sstats") != null) {
            getCommand("sstats").setExecutor(adminCommand);
            getCommand("sstats").setTabCompleter(adminCommand);
        }
    }

    private void startAutosave() {
        cancelAutosave();
        long intervalTicks = settings.getAutosaveMinutes() * 60L * 20L;
        autosaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, statsService::flushOnline, intervalTicks, intervalTicks).getTaskId();
        getLogger().info("Autosave scheduled every " + settings.getAutosaveMinutes() + " minute(s)");
    }

    private void cancelAutosave() {
        if (autosaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autosaveTaskId);
            autosaveTaskId = -1;
        }
    }

    private void startApiServer() {
        if (!settings.isApiEnabled()) {
            getLogger().info("HTTP API disabled in config.yml");
            apiServer = null;
            return;
        }
        apiServer = new ApiServer(this, statsService, settings, momentService, heatmapService, timelineService, serverHealthService);
        apiServer.start();
    }

    private void restartApiServer() {
        if (apiServer != null) {
            apiServer.stop();
        }
        startApiServer();
    }

    private void logStartupBanner(String action) {
        String version = getDescription().getVersion();
        getLogger().info("====================================");
        getLogger().info(" SMPStats " + action + " - v" + version);
        if (settings.isApiEnabled()) {
            getLogger().info(" API: enabled on port " + settings.getApiPort());
        } else {
            getLogger().info(" API: disabled");
        }
        getLogger().info(" Autosave: " + settings.getAutosaveMinutes() + " minute(s)");
        getLogger().info("====================================");
    }
}
