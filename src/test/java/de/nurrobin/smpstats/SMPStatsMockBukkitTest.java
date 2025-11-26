package de.nurrobin.smpstats;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SMPStatsMockBukkitTest {
    private ServerMock server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    @Test
    void pluginLoadsRegistersCommandsAndReloads() {
        server = MockBukkit.mock();
        Path configDir = server.getPluginsFolder().toPath().resolve("SMPStats");
        try {
            Files.createDirectories(configDir);
            Files.writeString(configDir.resolve("config.yml"), "api:\n  enabled: false\ntracking:\n  movement: true\n  biomes: true\n");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        SMPStats plugin = MockBukkit.load(SMPStats.class);

        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("stats"));
        assertNotNull(plugin.getCommand("smpstats"));
        assertNotNull(plugin.getCommand("sstats"));

        ConsoleCommandSender console = server.getConsoleSender();
        plugin.reloadPluginConfig(console);
        plugin.onDisable(); // should cleanly shutdown
    }
}
