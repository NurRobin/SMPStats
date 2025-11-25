package de.nurrobin.smpstats;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginYamlTest {

    @Test
    void pluginYamlHasCommands() {
        YamlConfiguration yaml = load("plugin.yml");
        assertEquals("de.nurrobin.smpstats.SMPStats", yaml.getString("main"));
        Map<String, Object> commands = yaml.getConfigurationSection("commands").getValues(false);
        assertTrue(commands.containsKey("stats"));
        assertTrue(commands.containsKey("smpstats"));
        assertTrue(commands.containsKey("sstats"));
    }

    private YamlConfiguration load(String name) {
        try (InputStreamReader reader = new InputStreamReader(getClass().getClassLoader().getResourceAsStream(name), StandardCharsets.UTF_8)) {
            assertNotNull(reader, "Resource " + name + " not found");
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
