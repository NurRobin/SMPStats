package de.nurrobin.smpstats.heatmap;

public class HeatmapBin {
    private final String type;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final double count;

    public HeatmapBin(String type, String world, int chunkX, int chunkZ, double count) {
        this.type = type;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.count = count;
    }

    public String getType() {
        return type;
    }

    public String getWorld() {
        return world;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public double getCount() {
        return count;
    }
}
