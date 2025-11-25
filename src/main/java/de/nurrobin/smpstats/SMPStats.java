package de.nurrobin.smpstats;

import de.nurrobin.smpstats.api.ApiServer;
import de.nurrobin.smpstats.commands.StatsCommand;
import de.nurrobin.smpstats.database.StatsStorage;
import de.nurrobin.smpstats.listeners.BlockListener;
import de.nurrobin.smpstats.listeners.CombatListener;
import de.nurrobin.smpstats.listeners.CraftingListener;
import de.nurrobin.smpstats.listeners.JoinQuitListener;
import de.nurrobin.smpstats.listeners.MovementListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;

public class SMPStats extends JavaPlugin {
    private StatsStorage storage;
    private StatsService statsService;
    private Settings settings;
    private ApiServer apiServer;
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

        registerListeners();
        registerCommands();
        for (Player online : Bukkit.getOnlinePlayers()) {
            statsService.handleJoin(online);
        }
        startAutosave();
        startApiServer();
    }

    @Override
    public void onDisable() {
        if (autosaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autosaveTaskId);
        }
        if (statsService != null) {
            statsService.shutdown();
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

        return new Settings(movement, blocks, kills, biomes, crafting, damage, consumption,
                apiEnabled, apiPort, apiKey, autosaveMinutes);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new JoinQuitListener(statsService), this);
        pm.registerEvents(new BlockListener(statsService, settings), this);
        pm.registerEvents(new CombatListener(statsService, settings), this);
        pm.registerEvents(new MovementListener(statsService, settings), this);
        pm.registerEvents(new CraftingListener(statsService, settings), this);
    }

    private void registerCommands() {
        StatsCommand statsCommand = new StatsCommand(this, statsService);
        if (getCommand("stats") != null) {
            getCommand("stats").setExecutor(statsCommand);
            getCommand("stats").setTabCompleter(statsCommand);
        } else {
            getLogger().warning("Command /stats is missing from plugin.yml");
        }
    }

    private void startAutosave() {
        long intervalTicks = settings.getAutosaveMinutes() * 60L * 20L;
        autosaveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(this, statsService::flushOnline, intervalTicks, intervalTicks).getTaskId();
        getLogger().info("Autosave scheduled every " + settings.getAutosaveMinutes() + " minute(s)");
    }

    private void startApiServer() {
        if (!settings.isApiEnabled()) {
            getLogger().info("HTTP API disabled in config.yml");
            return;
        }
        apiServer = new ApiServer(this, statsService, settings);
        apiServer.start();
    }
}
