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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SMPStatsConfigTest {
    private ServerMock server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    @Test
    void upgradesOldConfigAndParsesHotspotsFallback() throws Exception {
        server = MockBukkit.mock();
        Path pluginDir = server.getPluginsFolder().toPath().resolve("SMPStats");
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
        assertEquals(4, plugin.getConfig().getInt("config_version"));

        assertTrue(plugin.getSettings().isHeatmapEnabled());
        assertTrue(plugin.getTimelineService().isPresent());
        assertTrue(plugin.getDeathReplayService().isPresent());
        assertTrue(plugin.getServerHealthService().isPresent());
        assertTrue(plugin.getStoryService().isPresent());

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

    @SuppressWarnings("unchecked")
    private List<HotspotDefinition> invokeParseHotspots(SMPStats plugin, ConfigurationSection section) throws Exception {
        Method method = SMPStats.class.getDeclaredMethod("parseHotspots", ConfigurationSection.class);
        method.setAccessible(true);
        return (List<HotspotDefinition>) method.invoke(plugin, section);
    }
}
