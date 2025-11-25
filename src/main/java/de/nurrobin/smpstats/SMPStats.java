package de.nurrobin.smpstats;

import de.nurrobin.smpstats.api.ApiServer;
import de.nurrobin.smpstats.commands.StatsCommand;
import de.nurrobin.smpstats.commands.SmpstatsAdminCommand;
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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;

public class SMPStats extends JavaPlugin {
    private StatsStorage storage;
    private StatsService statsService;
    private Settings settings;
    private ApiServer apiServer;
    private MomentService momentService;
    private HeatmapService heatmapService;
    private int autosaveTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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

        registerListeners();
        registerCommands();
        for (Player online : Bukkit.getOnlinePlayers()) {
            statsService.handleJoin(online);
        }
        startAutosave();
        momentService.start();
        heatmapService.start();
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

    public void reloadPluginConfig(CommandSender sender) {
        reloadConfig();
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

        return new Settings(movement, blocks, kills, biomes, crafting, damage, consumption,
                apiEnabled, apiPort, apiKey, autosaveMinutes, skillWeights,
                momentsEnabled, diamondWindowSeconds, momentsFlushSeconds, heatmapEnabled, heatmapFlushMinutes, momentDefinitions);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new JoinQuitListener(statsService), this);
        pm.registerEvents(new BlockListener(this, statsService), this);
        pm.registerEvents(new CombatListener(this, statsService), this);
        pm.registerEvents(new MovementListener(this, statsService), this);
        pm.registerEvents(new CraftingListener(this, statsService), this);
        pm.registerEvents(new MomentListener(momentService), this);
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
        SmpstatsAdminCommand adminCommand = new SmpstatsAdminCommand(this);
        if (getCommand("smpstats") != null) {
            getCommand("smpstats").setExecutor(adminCommand);
            getCommand("smpstats").setTabCompleter(adminCommand);
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
        apiServer = new ApiServer(this, statsService, settings, momentService, heatmapService);
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
