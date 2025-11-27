package de.nurrobin.smpstats.commands;

import de.nurrobin.smpstats.SMPStats;
import de.nurrobin.smpstats.Settings;
import de.nurrobin.smpstats.StatsRecord;
import de.nurrobin.smpstats.StatsService;
import de.nurrobin.smpstats.gui.GuiManager;
import de.nurrobin.smpstats.gui.MainMenuGui;
import de.nurrobin.smpstats.health.ServerHealthService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SStatsCommandTest {

    private SMPStats pluginWithSettings() {
        SMPStats plugin = mock(SMPStats.class);
        Settings settings = mock(Settings.class);
        when(settings.isApiEnabled()).thenReturn(true);
        when(settings.getApiPort()).thenReturn(8765);
        when(settings.getAutosaveMinutes()).thenReturn(5);
        when(settings.isMomentsEnabled()).thenReturn(true);
        when(settings.isHeatmapEnabled()).thenReturn(true);
        when(settings.isSocialEnabled()).thenReturn(true);
        when(settings.getSocialNearbyRadius()).thenReturn(8);
        when(settings.isHealthEnabled()).thenReturn(false);
        when(settings.isStoryEnabled()).thenReturn(true);
        when(settings.isTrackMovement()).thenReturn(true);
        when(settings.isTrackBlocks()).thenReturn(true);
        when(settings.isTrackKills()).thenReturn(true);
        when(settings.isTrackBiomes()).thenReturn(true);
        when(settings.isTrackCrafting()).thenReturn(true);
        when(settings.isTrackDamage()).thenReturn(true);
        when(settings.isTrackConsumption()).thenReturn(false);
        when(plugin.getSettings()).thenReturn(settings);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        PluginDescriptionFile pdf = new PluginDescriptionFile("SMPStats", "1.0.0", "de.nurrobin.smpstats.SMPStats");
        when(plugin.getDescription()).thenReturn(pdf);
        return plugin;
    }

    @Test
    void showsInfoOutput() {
        SMPStats plugin = pluginWithSettings();
        StatsService stats = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand command = new SStatsCommand(plugin, stats, guiManager, healthService);

        CommandSender sender = mock(CommandSender.class);
        command.onCommand(sender, mock(Command.class), "sstats", new String[]{"info"});

        verify(sender, atLeast(1)).sendMessage(any(String.class));
    }

    @Test
    void handlesReloadPermissions() {
        SMPStats plugin = pluginWithSettings();
        StatsService stats = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand command = new SStatsCommand(plugin, stats, guiManager, healthService);

        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("smpstats.reload")).thenReturn(false);
        command.onCommand(sender, mock(Command.class), "sstats", new String[]{"reload"});
        verify(sender).sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.reload");

        when(sender.hasPermission("smpstats.reload")).thenReturn(true);
        command.onCommand(sender, mock(Command.class), "sstats", new String[]{"reload"});
        verify(plugin).reloadPluginConfig(sender);
    }

    @Test
    void handlesUserSubcommands() {
        SMPStats plugin = pluginWithSettings();
        StatsService stats = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand command = new SStatsCommand(plugin, stats, guiManager, healthService);

        CommandSender missingArgs = mock(CommandSender.class);
        command.onCommand(missingArgs, mock(Command.class), "sstats", new String[]{"user"});
        verify(missingArgs).sendMessage(ChatColor.RED + "Nutze: /sstats user <name> [reset|set <stat> <value>]");

        when(stats.getStatsByName("Alex")).thenReturn(Optional.empty());
        command.onCommand(missingArgs, mock(Command.class), "sstats", new String[]{"user", "Alex"});
        verify(missingArgs).sendMessage(ChatColor.RED + "Spieler 'Alex' nicht gefunden.");

        StatsRecord record = new StatsRecord(UUID.randomUUID(), "Alex");
        when(stats.getStatsByName("Alex")).thenReturn(Optional.of(record));

        CommandSender viewer = mock(CommandSender.class);
        try (MockedStatic<StatsFormatter> formatter = mockStatic(StatsFormatter.class)) {
            command.onCommand(viewer, mock(Command.class), "sstats", new String[]{"user", "Alex"});
            formatter.verify(() -> StatsFormatter.render(viewer, plugin, stats, record));
        }

        CommandSender noPerm = mock(CommandSender.class);
        when(noPerm.hasPermission("smpstats.edit")).thenReturn(false);
        command.onCommand(noPerm, mock(Command.class), "sstats", new String[]{"user", "Alex", "reset"});
        verify(noPerm).sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.edit");

        CommandSender editor = mock(CommandSender.class);
        when(editor.hasPermission("smpstats.edit")).thenReturn(true);
        when(stats.resetStats(record.getUuid())).thenReturn(true);
        command.onCommand(editor, mock(Command.class), "sstats", new String[]{"user", "Alex", "reset"});
        verify(stats).resetStats(record.getUuid());

        command.onCommand(editor, mock(Command.class), "sstats", new String[]{"user", "Alex", "set"});
        verify(editor).sendMessage(ChatColor.RED + "Nutze: /sstats user <name> set <stat> <value>");

        command.onCommand(editor, mock(Command.class), "sstats", new String[]{"user", "Alex", "set", "unknown", "1"});
        verify(editor).sendMessage(ChatColor.RED + "Unbekannter Stat: unknown");

        when(stats.setStat(record.getUuid(), StatField.DEATHS, Double.NaN)).thenReturn(false);
        command.onCommand(editor, mock(Command.class), "sstats", new String[]{"user", "Alex", "set", "deaths", "NaN"});
        verify(stats).setStat(record.getUuid(), StatField.DEATHS, Double.NaN);
        verify(editor).sendMessage(ChatColor.RED + "Konnte Stat nicht setzen.");

        CommandSender editor2 = mock(CommandSender.class);
        when(editor2.hasPermission("smpstats.edit")).thenReturn(true);
        when(stats.getStatsByName("Alex")).thenReturn(Optional.of(record));
        when(stats.setStat(record.getUuid(), StatField.DEATHS, 2.0)).thenReturn(true);
        command.onCommand(editor2, mock(Command.class), "sstats", new String[]{"user", "Alex", "set", "deaths", "2"});
        verify(stats).setStat(record.getUuid(), StatField.DEATHS, 2.0);
    }

    @Test
    void tabCompleteProvidesHints() {
        SMPStats plugin = pluginWithSettings();
        StatsService stats = mock(StatsService.class);
        when(stats.getOnlineNames()).thenReturn(List.of("Alex", "Bea"));
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand command = new SStatsCommand(plugin, stats, guiManager, healthService);

        List<String> root = command.onTabComplete(mock(CommandSender.class), mock(Command.class), "sstats", new String[]{""});
        assertTrue(root.contains("info"));

        List<String> users = command.onTabComplete(mock(CommandSender.class), mock(Command.class), "sstats", new String[]{"user", ""});
        assertTrue(users.contains("Alex"));

        List<String> actions = command.onTabComplete(mock(CommandSender.class), mock(Command.class), "sstats", new String[]{"user", "Alex", ""});
        assertTrue(actions.contains("reset"));

        List<String> statKeys = command.onTabComplete(mock(CommandSender.class), mock(Command.class), "sstats", new String[]{"user", "Alex", "set", ""});
        assertTrue(statKeys.contains("playtime_ms"));
    }

    @Test
    void handlesConsoleAndInvalidNumbers() {
        SMPStats plugin = pluginWithSettings();
        StatsService stats = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand command = new SStatsCommand(plugin, stats, guiManager, healthService);

        CommandSender console = mock(CommandSender.class);
        command.onCommand(console, mock(Command.class), "sstats", new String[]{});
        verify(console).sendMessage(ChatColor.RED + "Konsole: /sstats user <player>");

        StatsRecord record = new StatsRecord(UUID.randomUUID(), "Alex");
        when(stats.getStatsByName("Alex")).thenReturn(Optional.of(record));
        CommandSender editor = mock(CommandSender.class);
        when(editor.hasPermission("smpstats.edit")).thenReturn(true);
        command.onCommand(editor, mock(Command.class), "sstats", new String[]{"user", "Alex", "set", "deaths", "abc"});
        verify(editor).sendMessage(ChatColor.RED + "Wert muss eine Zahl sein.");
    }

    @Test
    void testNoArgsConsole() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);

        cmd.onCommand(sender, command, "sstats", new String[0]);

        verify(sender).sendMessage(contains("Konsole: /sstats user <player>"));
    }

    @Test
    void testNoArgsPlayer() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("TestPlayer");
        Command command = mock(Command.class);

        StatsRecord record = new StatsRecord(uuid, "TestPlayer");
        when(statsService.getStats(uuid)).thenReturn(Optional.of(record));

        try (MockedStatic<StatsFormatter> formatter = mockStatic(StatsFormatter.class)) {
            cmd.onCommand(player, command, "sstats", new String[0]);
            formatter.verify(() -> StatsFormatter.render(player, plugin, statsService, uuid, "TestPlayer"));
        }
    }

    @Test
    void testInfo() {
        SMPStats plugin = pluginWithSettings();
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);

        cmd.onCommand(sender, command, "sstats", new String[]{"info"});

        verify(sender, atLeastOnce()).sendMessage(contains("SMPStats"));
        verify(sender, atLeastOnce()).sendMessage(contains("Version"));
    }

    @Test
    void testReloadNoPerm() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("smpstats.reload")).thenReturn(false);
        Command command = mock(Command.class);

        cmd.onCommand(sender, command, "sstats", new String[]{"reload"});

        verify(sender).sendMessage(contains("fehlt die Berechtigung"));
        verify(plugin, never()).reloadPluginConfig(any());
    }

    @Test
    void testReloadSuccess() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("smpstats.reload")).thenReturn(true);
        Command command = mock(Command.class);

        cmd.onCommand(sender, command, "sstats", new String[]{"reload"});

        verify(plugin).reloadPluginConfig(sender);
    }

    @Test
    void testGuiCommandRejectsConsole() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender console = mock(CommandSender.class);
        Command command = mock(Command.class);

        cmd.onCommand(console, command, "sstats", new String[]{"gui"});

        verify(console).sendMessage(ChatColor.RED + "Only players can use the GUI.");
        verify(guiManager, never()).openGui(any(), any());
    }

    @Test
    void tabCompleteIncludesGuiOption() {
        SMPStats plugin = pluginWithSettings();
        StatsService stats = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand command = new SStatsCommand(plugin, stats, guiManager, healthService);

        List<String> root = command.onTabComplete(mock(CommandSender.class), mock(Command.class), "sstats", new String[]{""});
        assertTrue(root.contains("gui"));
    }
    
    @Test
    void testGuiCommandWithoutPermission() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        Player player = mock(Player.class);
        when(player.hasPermission("smpstats.gui")).thenReturn(false);
        Command command = mock(Command.class);

        cmd.onCommand(player, command, "sstats", new String[]{"gui"});

        verify(player).sendMessage(ChatColor.RED + "Dir fehlt die Berechtigung smpstats.gui");
        verify(guiManager, never()).openGui(any(), any());
    }
    
    @Test
    void testGuiCommandWithPermission() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        Player player = mock(Player.class);
        when(player.hasPermission("smpstats.gui")).thenReturn(true);
        Command command = mock(Command.class);

        // MainMenuGui constructor calls Bukkit.createInventory(), so we need to mock Bukkit
        try (MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            org.bukkit.inventory.Inventory mockInventory = mock(org.bukkit.inventory.Inventory.class);
            bukkit.when(() -> org.bukkit.Bukkit.createInventory(any(), anyInt(), any(net.kyori.adventure.text.Component.class)))
                    .thenReturn(mockInventory);
            
            cmd.onCommand(player, command, "sstats", new String[]{"gui"});

            // Verify openGui was called with the player and a MainMenuGui instance
            verify(guiManager).openGui(eq(player), any(MainMenuGui.class));
        }
    }
    
    @Test
    void testUnknownSubcommandShowsUsage() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);

        cmd.onCommand(sender, command, "sstats", new String[]{"unknown"});

        verify(sender).sendMessage(contains("Nutze"));
    }
    
    @Test
    void testUserResetSuccess() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("smpstats.edit")).thenReturn(true);
        Command command = mock(Command.class);
        
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        when(statsService.getStatsByName("Alex")).thenReturn(Optional.of(record));
        when(statsService.resetStats(uuid)).thenReturn(true);

        cmd.onCommand(sender, command, "sstats", new String[]{"user", "Alex", "reset"});

        verify(statsService).resetStats(uuid);
        verify(sender).sendMessage(contains("zurückgesetzt"));
    }
    
    @Test
    void testUserResetFailure() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("smpstats.edit")).thenReturn(true);
        Command command = mock(Command.class);
        
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        when(statsService.getStatsByName("Alex")).thenReturn(Optional.of(record));
        when(statsService.resetStats(uuid)).thenReturn(false);

        cmd.onCommand(sender, command, "sstats", new String[]{"user", "Alex", "reset"});

        verify(sender).sendMessage(contains("Konnte Stats nicht zurücksetzen"));
    }
    
    @Test
    void testUserSetSuccess() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("smpstats.edit")).thenReturn(true);
        Command command = mock(Command.class);
        
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        when(statsService.getStatsByName("Alex")).thenReturn(Optional.of(record));
        when(statsService.setStat(uuid, StatField.PLAYER_KILLS, 100.0)).thenReturn(true);

        cmd.onCommand(sender, command, "sstats", new String[]{"user", "Alex", "set", "player_kills", "100"});

        verify(statsService).setStat(uuid, StatField.PLAYER_KILLS, 100.0);
        verify(sender).sendMessage(contains("gesetzt auf 100"));
    }
    
    @Test
    void testUserUnknownAction() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        
        UUID uuid = UUID.randomUUID();
        StatsRecord record = new StatsRecord(uuid, "Alex");
        when(statsService.getStatsByName("Alex")).thenReturn(Optional.of(record));

        cmd.onCommand(sender, command, "sstats", new String[]{"user", "Alex", "unknownaction"});

        verify(sender).sendMessage(contains("Nutze"));
    }
    
    @Test
    void testTabCompleteEmpty() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);

        List<String> result = cmd.onTabComplete(mock(CommandSender.class), mock(Command.class), "sstats", new String[]{"user", "Alex", "set", "kills", ""});
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testTabCompleteUserActions() {
        SMPStats plugin = mock(SMPStats.class);
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);

        List<String> result = cmd.onTabComplete(mock(CommandSender.class), mock(Command.class), "sstats", new String[]{"user", "Alex", ""});
        assertTrue(result.contains("reset"));
        assertTrue(result.contains("set"));
    }
    
    @Test
    void testInfoShowsAllTrackingFlags() {
        SMPStats plugin = pluginWithSettings();
        StatsService statsService = mock(StatsService.class);
        GuiManager guiManager = mock(GuiManager.class);
        ServerHealthService healthService = mock(ServerHealthService.class);
        SStatsCommand cmd = new SStatsCommand(plugin, statsService, guiManager, healthService);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);

        cmd.onCommand(sender, command, "sstats", new String[]{"info"});

        // Verify multiple info lines are sent
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeast(5)).sendMessage(captor.capture());
        
        List<String> messages = captor.getAllValues();
        assertTrue(messages.stream().anyMatch(m -> m.contains("SMPStats")));
    }
}
