package de.nurrobin.smpstats.heatmap;

import org.bukkit.Location;

public class HotspotDefinition {
    private final String name;
    private final String world;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    public HotspotDefinition(String name, String world, int minX, int minZ, int maxX, int maxZ) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equalsIgnoreCase(world)) {
            return false;
        }
        int x = location.getBlockX();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}
