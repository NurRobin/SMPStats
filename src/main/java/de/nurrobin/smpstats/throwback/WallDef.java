package de.nurrobin.smpstats.throwback;

import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * One picture wall of "Bauten 2025". {@code corner} is the lower-left block in front of the wall as seen by a
 * viewer; frames hang on the wall behind it and face {@code facing}. The picture is {@code images/<name>.png}.
 */
public record WallDef(String name, int x, int y, int z, BlockFace facing, int width, int height, String title,
                      String builders) {

    /** Horizontal direction to the viewer's right while looking at the wall. */
    public BlockFace right() {
        return switch (facing) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            default -> BlockFace.SOUTH;
        };
    }

    public static List<WallDef> readAll(ConfigurationSection section, java.util.logging.Logger logger) {
        List<WallDef> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        for (java.util.Map<?, ?> m : section.getMapList("walls")) {
            try {
                String name = String.valueOf(m.get("name"));
                if (!name.matches("[A-Za-z0-9_.-]{1,64}")) {
                    logger.warning("Throwback: Bilderwand mit ungültigem Namen übersprungen: " + name);
                    continue;
                }
                java.util.Map<?, ?> corner = (java.util.Map<?, ?>) m.get("corner");
                BlockFace face = BlockFace.valueOf(String.valueOf(m.get("facing")).toUpperCase(java.util.Locale.ROOT));
                if (face != BlockFace.NORTH && face != BlockFace.SOUTH && face != BlockFace.EAST && face != BlockFace.WEST) {
                    throw new IllegalArgumentException("facing muss NORTH, SOUTH, EAST oder WEST sein");
                }
                int width = Math.max(1, Math.min(16, ((Number) m.get("width")).intValue()));
                int height = Math.max(1, Math.min(16, ((Number) m.get("height")).intValue()));
                out.add(new WallDef(name, ((Number) corner.get("x")).intValue(), ((Number) corner.get("y")).intValue(),
                        ((Number) corner.get("z")).intValue(), face, width, height,
                        m.get("title") == null ? name : String.valueOf(m.get("title")),
                        m.get("builders") == null ? "" : String.valueOf(m.get("builders"))));
            } catch (RuntimeException e) {
                logger.warning("Throwback: Bilderwand übersprungen (" + e.getMessage() + "): " + m);
            }
        }
        return out;
    }

    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", name);
        m.put("corner", java.util.Map.of("x", x, "y", y, "z", z));
        m.put("facing", facing.name());
        m.put("width", width);
        m.put("height", height);
        m.put("title", title);
        m.put("builders", builders);
        return m;
    }
}
