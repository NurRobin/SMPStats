package de.nurrobin.smpstats;

import de.nurrobin.smpstats.season.SeasonImporter;
import de.nurrobin.smpstats.season.SeasonService;
import de.nurrobin.smpstats.season.SeasonStore;
import de.nurrobin.smpstats.throwback.CachedProgressRepository;
import de.nurrobin.smpstats.throwback.HallWorld;
import de.nurrobin.smpstats.throwback.MannequinSceneRenderer;
import de.nurrobin.smpstats.throwback.ThrowbackCommand;
import de.nurrobin.smpstats.throwback.ThrowbackEditListener;
import de.nurrobin.smpstats.throwback.ThrowbackEditor;
import de.nurrobin.smpstats.throwback.ThrowbackListener;
import de.nurrobin.smpstats.throwback.ThrowbackService;
import de.nurrobin.smpstats.throwback.ThrowbackSettings;
import de.nurrobin.smpstats.throwback.ThrowbackStore;
import de.nurrobin.smpstats.api.ApiServer;
import de.nurrobin.smpstats.commands.StatsCommand;
import de.nurrobin.smpstats.commands.SStatsCommand;
import de.nurrobin.smpstats.dashboard.WebDashboardServer;
import de.nurrobin.smpstats.database.RetentionService;
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
import de.nurrobin.smpstats.health.HealthThresholds;
import de.nurrobin.smpstats.story.StoryService;
import de.nurrobin.smpstats.gui.GuiManager;
import de.nurrobin.smpstats.gui.AnimatedBorderService;
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
    private static final int CONFIG_VERSION = 9;
    private StatsStorage storage;
    private StatsService statsService;
    private Settings settings;
    private ApiServer apiServer;
    private WebDashboardServer dashboardServer;
    private MomentService momentService;
    private HeatmapService heatmapService;
    private SocialStatsService socialStatsService;
    private TimelineService timelineService;
    private DeathReplayService deathReplayService;
    private ServerHealthService serverHealthService;
    private StoryService storyService;
    private GuiManager guiManager;
    private AnimatedBorderService animatedBorderService;
    private RetentionService retentionService;
    private SeasonService seasonService;
    private ThrowbackService throwbackService;
    private MannequinSceneRenderer throwbackRenderer;
    private ThrowbackEditor throwbackEditor;
    private de.nurrobin.smpstats.database.WriteQueue writeQueue;
    private int autosaveTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String configError = validateConfigFile();
        if (configError != null) {
            // Leave the broken file untouched (api_key / admin password are in there) and keep a copy.
            backupConfig("config.yml.broken");
            getLogger().severe("config.yml is not valid YAML, SMPStats stays disabled: " + configError);
            getLogger().severe("Fix config.yml (a copy was saved as config.yml.broken) and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
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

        this.writeQueue = de.nurrobin.smpstats.database.WriteQueue.async(getLogger());
        this.statsService = new StatsService(this, storage, settings, writeQueue);
        this.momentService = new MomentService(this, storage, settings);
        this.heatmapService = new HeatmapService(this, storage, settings);
        this.socialStatsService = new SocialStatsService(this, storage, settings);
        this.timelineService = new TimelineService(this, storage, settings);
        this.deathReplayService = new DeathReplayService(this, storage, settings, writeQueue);
        this.serverHealthService = new ServerHealthService(this, settings);
        this.storyService = new StoryService(this, statsService, storage, momentService, settings);
        this.guiManager = new GuiManager(this);
        this.animatedBorderService = new AnimatedBorderService(this);
        this.retentionService = new RetentionService(this, storage, settings);
        this.seasonService = new SeasonService(getLogger(), getDataFolder().toPath().resolve("seasons"),
                new SeasonStore(storage), new SeasonImporter(),
                task -> Bukkit.getScheduler().runTaskAsynchronously(this, task));

        startThrowback();

        registerListeners();
        registerCommands();
        for (Player online : Bukkit.getOnlinePlayers()) {
            statsService.handleJoin(online);
        }
        startAutosave();
        momentService.start();
        heatmapService.start();
        socialStatsService.start();
        retentionService.start();
        seasonService.autoImport();
        if (throwbackService != null) {
            throwbackService.start();
        }
        if (deathReplayService != null) {
            deathReplayService.start();
        }
        if (serverHealthService != null) {
            serverHealthService.start();
        }
        if (storyService != null) {
            storyService.start();
        }
        if (animatedBorderService != null) {
            animatedBorderService.start();
        }
        startApiServer();
        startDashboardServer();
        logStartupBanner("Aktiv");
    }

    @Override
    public void onDisable() {
        cancelAutosave();
        if (throwbackEditor != null) {
            throwbackEditor.shutdown();
        }
        if (throwbackService != null) {
            throwbackService.shutdown();
        }
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
        if (retentionService != null) {
            retentionService.shutdown();
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
        if (animatedBorderService != null) {
            animatedBorderService.stop();
        }
        if (apiServer != null) {
            apiServer.stop();
        }
        if (dashboardServer != null) {
            dashboardServer.stop();
        }
        if (writeQueue != null) {
            int lost = writeQueue.shutdown(10_000L);
            if (lost > 0) {
                getLogger().warning(lost + " queued database write(s) could not be flushed on shutdown.");
            }
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

    public java.util.Optional<de.nurrobin.smpstats.moments.MomentService> getMomentService() {
        return java.util.Optional.ofNullable(momentService);
    }

    public java.util.Optional<StatsStorage> getStatsStorage() {
        return java.util.Optional.ofNullable(storage);
    }

    public java.util.Optional<HeatmapService> getHeatmapService() {
        return java.util.Optional.ofNullable(heatmapService);
    }

    public ThrowbackService getThrowbackService() {
        return throwbackService;
    }

    public SeasonService getSeasonService() {
        return seasonService;
    }

    public StatsService getStatsService() {
        return statsService;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public java.util.Optional<AnimatedBorderService> getAnimatedBorderService() {
        return java.util.Optional.ofNullable(animatedBorderService);
    }

    public void reloadPluginConfig(CommandSender sender) {
        String configError = validateConfigFile();
        if (configError != null) {
            getLogger().severe("Reload aborted, config.yml is not valid YAML: " + configError);
            sender.sendMessage(ChatColor.RED + "config.yml ist ungültig, Reload abgebrochen (alte Einstellungen bleiben aktiv): " + configError);
            return;
        }
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
        if (retentionService != null) {
            retentionService.updateSettings(settings);
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
        if (throwbackService != null) {
            throwbackService.reload(readThrowbackSettings());
        }
        startAutosave();
        restartApiServer();
        restartDashboardServer();
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
        String apiBindAddress = config.getString("api.bind_address", "127.0.0.1");
        int apiPort = config.getInt("api.port", 8765);
        String apiKey = config.getString("api.api_key", "");
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
        double heatmapDecayHalfLifeHours = Math.max(0, config.getDouble("heatmap.decay_half_life_hours", 0.0));

        MomentConfigParser parser = new MomentConfigParser();
        java.util.List<de.nurrobin.smpstats.moments.MomentDefinition> momentDefinitions = parser.parse(config.getConfigurationSection("moments"));
        java.util.List<HotspotDefinition> hotspots = parseHotspots(config);

        boolean socialEnabled = config.getBoolean("social.enabled", true);
        int socialSampleSeconds = Math.max(1, config.getInt("social.sample_seconds", 5));
        int socialNearbyRadius = Math.max(1, config.getInt("social.nearby_radius", 16));

        boolean timelineEnabled = config.getBoolean("timeline.enabled", true);

        boolean deathReplayEnabled = config.getBoolean("death_replay.enabled", true);
        boolean deathReplayInventoryItems = config.getBoolean("death_replay.include_inventory_items", true);
        int deathReplayNearbyRadius = Math.max(1, config.getInt("death_replay.nearby_radius", 16));
        int deathReplayLimit = Math.max(1, config.getInt("death_replay.limit", 20));

        boolean healthEnabled = config.getBoolean("health.enabled", true);
        int healthSampleMinutes = Math.max(1, config.getInt("health.sample_minutes", 15));
        double healthChunkWeight = config.getDouble("health.weights.chunk", 0.02);
        double healthEntityWeight = config.getDouble("health.weights.entity", 0.005);
        double healthHopperWeight = config.getDouble("health.weights.hopper", 0.2);
        double healthRedstoneWeight = config.getDouble("health.weights.redstone", 0.1);
        
        // Parse health thresholds
        HealthThresholds healthThresholds = parseHealthThresholds(config);

        boolean storyEnabled = config.getBoolean("story.enabled", true);;
        int storyIntervalDays = Math.max(1, config.getInt("story.interval_days", 7));
        int storySummaryHour = Math.min(23, Math.max(0, config.getInt("story.summary_hour", 6)));
        String storyWebhookUrl = config.getString("story.webhook_url", "");
        int storyTopLimit = Math.max(1, config.getInt("story.top_limit", 5));
        int storyRecentMoments = Math.max(0, config.getInt("story.recent_moments", 10));
        
        // Parse dashboard settings
        Settings.DashboardSettings dashboardSettings = parseDashboardSettings(config);
        
        // GUI settings
        boolean guiAnimatedBordersEnabled = config.getBoolean("gui.animated_borders", true);

        return new Settings(movement, blocks, kills, biomes, crafting, damage, consumption,
                apiEnabled, apiBindAddress, apiPort, apiKey, autosaveMinutes, skillWeights,
                momentsEnabled, diamondWindowSeconds, momentsFlushSeconds, heatmapEnabled, heatmapFlushMinutes, heatmapDecayHalfLifeHours, momentDefinitions, hotspots,
                socialEnabled, socialSampleSeconds, socialNearbyRadius, timelineEnabled,
                deathReplayEnabled, deathReplayInventoryItems, deathReplayNearbyRadius, deathReplayLimit,
                healthEnabled, healthSampleMinutes, healthChunkWeight, healthEntityWeight, healthHopperWeight, healthRedstoneWeight, healthThresholds,
                storyEnabled, storyIntervalDays, storySummaryHour, storyWebhookUrl, storyTopLimit, storyRecentMoments,
                dashboardSettings, guiAnimatedBordersEnabled)
                .withRetention(new Settings.RetentionSettings(
                        Math.max(0, config.getInt("retention.heatmap_events_days", 21)),
                        Math.max(0, config.getInt("retention.moments_days", 0)),
                        Math.max(0, config.getInt("retention.death_replays_days", 0))));
    }
    
    private Settings.DashboardSettings parseDashboardSettings(FileConfiguration config) {
        boolean enabled = config.getBoolean("dashboard.enabled", true);
        String bindAddress = config.getString("dashboard.bind_address", "127.0.0.1");
        int port = config.getInt("dashboard.port", 8080);
        
        Settings.PublicSettings publicSettings = new Settings.PublicSettings(
                config.getBoolean("dashboard.public.enabled", true),
                config.getBoolean("dashboard.public.show_online_players", true),
                config.getBoolean("dashboard.public.show_leaderboards", true),
                config.getBoolean("dashboard.public.show_recent_moments", true),
                config.getBoolean("dashboard.public.show_server_stats", true)
        );
        
        Settings.AdminSettings adminSettings = new Settings.AdminSettings(
                config.getBoolean("dashboard.admin.enabled", true),
                config.getString("dashboard.admin.password", ""),
                config.getInt("dashboard.admin.session_timeout_minutes", 60),
                config.getBoolean("dashboard.admin.show_health_metrics", true),
                config.getBoolean("dashboard.admin.show_heatmaps", true),
                config.getBoolean("dashboard.admin.show_social_data", true),
                config.getBoolean("dashboard.admin.show_death_replays", true)
        );
        
        boolean trustProxy = config.getBoolean("dashboard.trust_proxy", false);
        boolean secureCookie = config.getBoolean("dashboard.secure_cookie", true);

        return new Settings.DashboardSettings(enabled, bindAddress, port, publicSettings, adminSettings,
                trustProxy, secureCookie);
    }
    
    private HealthThresholds parseHealthThresholds(FileConfiguration config) {
        HealthThresholds defaults = HealthThresholds.defaults();
        
        HealthThresholds.MetricThreshold tps = new HealthThresholds.MetricThreshold(
                config.getDouble("health.thresholds.tps.good", 19.0),
                config.getDouble("health.thresholds.tps.acceptable", 18.0),
                config.getDouble("health.thresholds.tps.warning", 15.0),
                config.getDouble("health.thresholds.tps.critical", 10.0),
                true);
        
        HealthThresholds.MetricThreshold memory = new HealthThresholds.MetricThreshold(
                config.getDouble("health.thresholds.memory_percent.good", 50),
                config.getDouble("health.thresholds.memory_percent.acceptable", 70),
                config.getDouble("health.thresholds.memory_percent.warning", 85),
                config.getDouble("health.thresholds.memory_percent.critical", 95),
                false);
        
        HealthThresholds.MetricThreshold chunks = new HealthThresholds.MetricThreshold(
                config.getDouble("health.thresholds.chunks.good", 500),
                config.getDouble("health.thresholds.chunks.acceptable", 1000),
                config.getDouble("health.thresholds.chunks.warning", 2000),
                config.getDouble("health.thresholds.chunks.critical", 4000),
                false);
        
        HealthThresholds.MetricThreshold entities = new HealthThresholds.MetricThreshold(
                config.getDouble("health.thresholds.entities.good", 500),
                config.getDouble("health.thresholds.entities.acceptable", 1500),
                config.getDouble("health.thresholds.entities.warning", 3000),
                config.getDouble("health.thresholds.entities.critical", 5000),
                false);
        
        HealthThresholds.MetricThreshold hoppers = new HealthThresholds.MetricThreshold(
                config.getDouble("health.thresholds.hoppers.good", 200),
                config.getDouble("health.thresholds.hoppers.acceptable", 500),
                config.getDouble("health.thresholds.hoppers.warning", 1000),
                config.getDouble("health.thresholds.hoppers.critical", 2000),
                false);
        
        HealthThresholds.MetricThreshold redstone = new HealthThresholds.MetricThreshold(
                config.getDouble("health.thresholds.redstone.good", 500),
                config.getDouble("health.thresholds.redstone.acceptable", 1000),
                config.getDouble("health.thresholds.redstone.warning", 2000),
                config.getDouble("health.thresholds.redstone.critical", 5000),
                false);
        
        HealthThresholds.MetricThreshold costIndex = new HealthThresholds.MetricThreshold(
                config.getDouble("health.thresholds.cost_index.good", 25),
                config.getDouble("health.thresholds.cost_index.acceptable", 50),
                config.getDouble("health.thresholds.cost_index.warning", 75),
                config.getDouble("health.thresholds.cost_index.critical", 90),
                false);
        
        return new HealthThresholds(tps, memory, chunks, entities, hoppers, redstone, costIndex);
    }

    /**
     * Parses config.yml strictly. Bukkit's own loader swallows YAML errors and hands back an empty
     * configuration, which the migration would then fill with defaults and write over the file.
     *
     * @return null when the file is readable, otherwise a short error description
     */
    String validateConfigFile() {
        java.io.File file = new java.io.File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            return null;
        }
        try {
            new YamlConfiguration().load(file);
            return null;
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            return String.valueOf(e.getMessage()).lines().findFirst().orElse("unknown error");
        }
    }

    private void backupConfig(String targetName) {
        java.io.File file = new java.io.File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            return;
        }
        try {
            java.nio.file.Files.copy(file.toPath(), file.toPath().resolveSibling(targetName),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            getLogger().warning("Could not write " + targetName + ": " + e.getMessage());
        }
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
            backupConfig("config.yml.bak");
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

    private ThrowbackSettings readThrowbackSettings() {
        return ThrowbackSettings.read(getConfig().getConfigurationSection("throwback"), getLogger());
    }

    private void startThrowback() {
        try {
            ThrowbackSettings throwbackSettings = readThrowbackSettings();
            ThrowbackStore progressStore = new ThrowbackStore(storage);
            MannequinSceneRenderer renderer = new MannequinSceneRenderer(this);
            long hallTime = 18000L;
            this.throwbackService = new ThrowbackService(this, seasonService,
                    new CachedProgressRepository(progressStore, getLogger()), throwbackSettings, renderer,
                    new HallWorld(this, throwbackSettings.world(), hallTime),
                    () -> !Bukkit.getOnlineMode(), System::currentTimeMillis);
            this.throwbackRenderer = renderer;
            this.throwbackEditor = new ThrowbackEditor(this, throwbackService, renderer);
            seasonService.onImported(season -> {
                if (season.equals(throwbackService.settings().season())) {
                    throwbackService.invalidateBoard();
                }
            });
        } catch (RuntimeException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Throwback-Halle konnte nicht gestartet werden, sie bleibt aus", e);
            this.throwbackService = null;
        }
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new JoinQuitListener(statsService), this);
        pm.registerEvents(new BlockListener(this, statsService), this);
        pm.registerEvents(new MovementListener(this, statsService), this);
        pm.registerEvents(new CombatListener(this, statsService, socialStatsService), this);
        pm.registerEvents(new CraftingListener(this, statsService), this);
        pm.registerEvents(new MomentListener(momentService, deathReplayService), this);
        pm.registerEvents(new HeatmapListener(heatmapService), this);
        pm.registerEvents(guiManager, this);
        if (throwbackService != null) {
            pm.registerEvents(new ThrowbackListener(this, throwbackService, throwbackRenderer), this);
            pm.registerEvents(new ThrowbackEditListener(throwbackEditor, throwbackService), this);
        }
    }

    private void registerCommands() {
        StatsCommand statsCommand = new StatsCommand(this, statsService);
        Objects.requireNonNull(getCommand("stats")).setExecutor(statsCommand);
        Objects.requireNonNull(getCommand("stats")).setTabCompleter(statsCommand);
        SStatsCommand adminCommand = new SStatsCommand(this, statsService, guiManager, serverHealthService);
        Objects.requireNonNull(getCommand("sstats")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("sstats")).setTabCompleter(adminCommand);
        Objects.requireNonNull(getCommand("smpstats")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("smpstats")).setTabCompleter(adminCommand);
        if (throwbackService != null && getCommand("throwback") != null) {
            ThrowbackCommand throwbackCommand = new ThrowbackCommand(this, throwbackService, this::readThrowbackSettings, throwbackEditor);
            Objects.requireNonNull(getCommand("throwback")).setExecutor(throwbackCommand);
            Objects.requireNonNull(getCommand("throwback")).setTabCompleter(throwbackCommand);
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
    
    private void startDashboardServer() {
        Settings.DashboardSettings dashSettings = settings.getDashboardSettings();
        if (!dashSettings.enabled()) {
            getLogger().info("Web Dashboard disabled in config.yml");
            dashboardServer = null;
            return;
        }
        dashboardServer = new WebDashboardServer(this, statsService, settings, momentService, heatmapService, serverHealthService);
        dashboardServer.start();
    }
    
    private void restartDashboardServer() {
        if (dashboardServer != null) {
            dashboardServer.stop();
        }
        startDashboardServer();
    }

    private void logStartupBanner(String action) {
        String version = getDescription().getVersion();
        getLogger().info("====================================");
        getLogger().info(" SMPStats " + action + " - v" + version);
        if (apiServer != null && apiServer.isRunning()) {
            getLogger().info(" API: running on port " + settings.getApiPort());
        } else if (settings.isApiEnabled()) {
            getLogger().info(" API: not running (see warning above)");
        } else {
            getLogger().info(" API: disabled");
        }
        Settings.DashboardSettings dashSettings = settings.getDashboardSettings();
        if (dashSettings.enabled()) {
            getLogger().info(" Dashboard: enabled on port " + dashSettings.port());
        } else {
            getLogger().info(" Dashboard: disabled");
        }
        getLogger().info(" Autosave: " + settings.getAutosaveMinutes() + " minute(s)");
        getLogger().info("====================================");
    }
}
