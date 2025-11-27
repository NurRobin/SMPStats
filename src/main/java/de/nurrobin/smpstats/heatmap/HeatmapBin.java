package de.nurrobin.smpstats.heatmap;

public class HeatmapBin {
    private final String type;
    private final String world;
    private final int x;
    private final int z;
    private final int gridSize;
    private final double count;

    public HeatmapBin(String type, String world, int x, int z, int gridSize, double count) {
        this.type = type;
        this.world = world;
        this.x = x;
        this.z = z;
        this.gridSize = gridSize;
        this.count = count;
    }

    public HeatmapBin(String type, String world, int chunkX, int chunkZ, double count) {
        this(type, world, chunkX, chunkZ, 16, count);
    }

    public String getType() {
        return type;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public int getGridSize() {
        return gridSize;
    }

    public int getChunkX() {
        return x;
    }

    public int getChunkZ() {
        return z;
    }

    public double getCount() {
        return count;
    }
}
