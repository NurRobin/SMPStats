package de.nurrobin.smpstats.throwback;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** A position with a facing in some world; the world comes from context (hall world or main world). */
public record Pos(double x, double y, double z, float yaw) {

    public static Pos of(Location l) {
        return new Pos(l.getX(), l.getY(), l.getZ(), l.getYaw());
    }

    public Location at(World world) {
        return new Location(world, x, y, z, yaw, 0f);
    }

    public static Pos read(ConfigurationSection s) {
        if (s == null || !s.isSet("x") || !s.isSet("y") || !s.isSet("z")) {
            return null;
        }
        return new Pos(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw", 0));
    }

    public void write(ConfigurationSection s) {
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
    }
}
