package de.nurrobin.smpstats;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

    @Disabled("MockBukkit build still mismatches Paper 1.21.10 materials; update when compatible")
    @Test
    void pluginLoadsRegistersCommandsAndReloads() {
        server = MockBukkit.mock();
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
