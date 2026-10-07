package de.nurrobin.smpstats.throwback;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/** The {@code throwback} section of config.yml. */
public record ThrowbackSettings(boolean enabled, String world, String season, int minPlaytimeMinutes, int rankBadgeTop,
                                String rulesVersion, String privacyUrl, boolean matchByNameOffline,
                                int unloadDelaySeconds, String imagesFolder, String textsFile, String exitWorld,
                                Pos exit, SpawnPortal spawnPortal, HallLayout layout) {

    /** The entrance in the main world: walking into the region starts a visit; {@code exit} is where visitors land. */
    public record SpawnPortal(String world, Region region, Pos exit) {
    }

    public static ThrowbackSettings read(ConfigurationSection s, Logger logger) {
        if (s == null) {
            s = new org.bukkit.configuration.file.YamlConfiguration();
        }
        String world = s.getString("world", "throwback_hall");
        if (!world.matches("[A-Za-z0-9_.-]{1,64}")) {
            logger.warning("Throwback: ungültiger Weltname '" + world + "', nutze throwback_hall");
            world = "throwback_hall";
        }
        ConfigurationSection exitSection = s.getConfigurationSection("exit");
        ConfigurationSection portalSection = s.getConfigurationSection("spawn_portal");
        SpawnPortal portal = null;
        if (portalSection != null && Region.read(portalSection) != null) {
            portal = new SpawnPortal(portalSection.getString("world", "world"), Region.read(portalSection),
                    Pos.read(portalSection.getConfigurationSection("exit")));
        }
        return new ThrowbackSettings(
                s.getBoolean("enabled", true),
                world,
                s.getString("season", "2025"),
                Math.max(0, s.getInt("min_playtime_minutes", 60)),
                Math.max(0, s.getInt("rank_badge_top", 15)),
                s.getString("rules_version", "2026-1"),
                s.getString("privacy_url", ""),
                s.getBoolean("match_by_name_offline", true),
                Math.max(0, s.getInt("unload_delay_seconds", 60)),
                s.getString("images_folder", "throwback/images"),
                s.getString("texts_file", "throwback/texts.yml"),
                exitSection == null ? "" : exitSection.getString("world", ""),
                exitSection == null ? null : Pos.read(exitSection),
                portal,
                HallLayout.read(s.getConfigurationSection("layout"), logger));
    }
}
