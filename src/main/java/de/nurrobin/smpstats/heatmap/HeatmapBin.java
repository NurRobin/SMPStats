package de.nurrobin.smpstats.heatmap;

public class HeatmapBin {
    private final HeatmapType type;
    private final String world;
    private final int chunkX;
    private final int chunkZ;
    private final long count;

    public HeatmapBin(HeatmapType type, String world, int chunkX, int chunkZ, long count) {
        this.type = type;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.count = count;
    }

    public HeatmapType getType() {
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

    public long getCount() {
        return count;
    }
}
