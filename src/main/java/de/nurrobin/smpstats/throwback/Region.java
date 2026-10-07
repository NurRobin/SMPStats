package de.nurrobin.smpstats.throwback;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/** Axis-aligned box in block coordinates (both corners inclusive of the whole block). */
public record Region(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Region of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Region(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX + 1 && y >= minY && y < maxY + 1 && z >= minZ && z < maxZ + 1;
    }

    public boolean contains(Location l) {
        return contains(l.getX(), l.getY(), l.getZ());
    }

    public double centerX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double centerZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    public static Region read(ConfigurationSection s) {
        if (s == null || !s.isConfigurationSection("min") || !s.isConfigurationSection("max")) {
            return null;
        }
        ConfigurationSection a = s.getConfigurationSection("min");
        ConfigurationSection b = s.getConfigurationSection("max");
        return of(a.getInt("x"), a.getInt("y"), a.getInt("z"), b.getInt("x"), b.getInt("y"), b.getInt("z"));
    }

    public void write(ConfigurationSection s) {
        s.set("min.x", minX);
        s.set("min.y", minY);
        s.set("min.z", minZ);
        s.set("max.x", maxX);
        s.set("max.y", maxY);
        s.set("max.z", maxZ);
    }
}
