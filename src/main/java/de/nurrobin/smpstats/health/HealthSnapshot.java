package de.nurrobin.smpstats.health;

import java.util.List;
import java.util.Map;

public record HealthSnapshot(long timestamp,
                             double tps,
                             long memoryUsed,
                             long memoryMax,
                             int chunks,
                             int entities,
                             int hoppers,
                             int redstone,
                             double costIndex,
                             Map<String, WorldBreakdown> worlds,
                             List<HotChunk> hotChunks) {
    public record WorldBreakdown(int chunks, int entities, int hoppers, int redstone) {
    }

    public record HotChunk(String world, int x, int z, int entityCount, int tileEntityCount, String topOwner) {
    }
}
