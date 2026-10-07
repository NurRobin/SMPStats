package de.nurrobin.smpstats;

import de.nurrobin.smpstats.heatmap.HotspotDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SMPStatsConfigTest {
    private ServerMock server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    /** MockBukkit names the data folder "<name>-<version>", so derive it from plugin.yml. */
    private Path pluginDir() throws Exception {
        try (var in = SMPStats.class.getResourceAsStream("/plugin.yml")) {
            var desc = new org.bukkit.plugin.PluginDescriptionFile(in);
            return server.getPluginsFolder().toPath().resolve(desc.getName() + "-" + desc.getVersion());
        }
    }

    @Test
    void upgradesOldConfigAndParsesHotspotsFallback() throws Exception {
        server = MockBukkit.mock();
        Path pluginDir = pluginDir();
        Files.createDirectories(pluginDir);
        Files.writeString(pluginDir.resolve("config.yml"), """
                config_version: 1
                api:
                  enabled: false
                heatmap:
                  hotspots:
                    spawn:
                      name: Spawn
                      world: world
                      min:
                        x: 0
                        z: 0
                      max:
                        x: 16
                        z: 16
                moments:
                  enabled: false
                social:
                  enabled: false
                timeline:
                  enabled: true
                death_replay:
                  enabled: false
                health:
                  enabled: false
                story:
                  enabled: false
                """);

        SMPStats plugin = MockBukkit.load(SMPStats.class);
        assertEquals(9, plugin.getConfig().getInt("config_version"));

        assertTrue(plugin.getSettings().isHeatmapEnabled());
        assertTrue(plugin.getTimelineService().isPresent());
        assertTrue(plugin.getDeathReplayService().isPresent());
        assertTrue(plugin.getServerHealthService().isPresent());
        assertTrue(plugin.getStoryService().isPresent());

        // The real loadSettings() path must pick the configured hotspot up, not only the helper.
        assertEquals(1, plugin.getSettings().getHeatmapHotspots().size());
        assertEquals("Spawn", plugin.getSettings().getHeatmapHotspots().getFirst().getName());

        List<HotspotDefinition> parsed = invokeParseHotspots(plugin, plugin.getConfig());
        assertEquals(1, parsed.size());
        assertEquals("Spawn", parsed.getFirst().getName());

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("heatmap.hotspots", List.of("dummy"));
        ConfigurationSection hotspotsSection = yaml.createSection("hotspots");
        ConfigurationSection zone = hotspotsSection.createSection("zone");
        zone.set("name", "Fallback");
        zone.set("world", "world_nether");
        zone.set("min.x", 1);
        zone.set("min.z", 2);
        zone.set("max.x", 3);
        zone.set("max.z", 4);
        List<HotspotDefinition> fallback = invokeParseHotspots(plugin, yaml);
        assertEquals("Fallback", fallback.getFirst().getName());

        plugin.onDisable();
    }

    private static final String BROKEN_YAML = """
            api:
              enabled: true
              api_key: "secret-key"
             broken: [unclosed
            dashboard:
              admin:
                password: hunter2
            """;

    @Test
    void brokenConfigIsNeverOverwrittenOnStartup() throws Exception {
        server = MockBukkit.mock();
        Path pluginDir = pluginDir();
        Files.createDirectories(pluginDir);
        Files.writeString(pluginDir.resolve("config.yml"), BROKEN_YAML);

        SMPStats plugin = MockBukkit.load(SMPStats.class);

        assertEquals(BROKEN_YAML, Files.readString(pluginDir.resolve("config.yml")));
        assertEquals(BROKEN_YAML, Files.readString(pluginDir.resolve("config.yml.broken")));
        assertFalse(plugin.isEnabled());
    }

    @Test
    void brokenConfigAbortsReloadAndKeepsSettings() throws Exception {
        server = MockBukkit.mock();
        Path pluginDir = pluginDir();
        Files.createDirectories(pluginDir);
        Files.writeString(pluginDir.resolve("config.yml"), "config_version: 8\napi:\n  enabled: false\n");
        SMPStats plugin = MockBukkit.load(SMPStats.class);
        Settings before = plugin.getSettings();

        Files.writeString(pluginDir.resolve("config.yml"), BROKEN_YAML);
        org.bukkit.command.CommandSender sender = org.mockito.Mockito.mock(org.bukkit.command.CommandSender.class);
        plugin.reloadPluginConfig(sender);

        assertEquals(BROKEN_YAML, Files.readString(pluginDir.resolve("config.yml")));
        assertSame(before, plugin.getSettings());
        org.mockito.Mockito.verify(sender).sendMessage(org.mockito.ArgumentMatchers.contains("ungültig"));
        plugin.onDisable();
    }

    @SuppressWarnings("unchecked")
    private List<HotspotDefinition> invokeParseHotspots(SMPStats plugin, ConfigurationSection section) throws Exception {
        Method method = SMPStats.class.getDeclaredMethod("parseHotspots", ConfigurationSection.class);
        method.setAccessible(true);
        return (List<HotspotDefinition>) method.invoke(plugin, section);
    }
}
