package de.nurrobin.smpstats.health;

import java.util.Map;

public record HealthSnapshot(long timestamp,
                             int chunks,
                             int entities,
                             int hoppers,
                             int redstone,
                             double costIndex,
                             Map<String, WorldBreakdown> worlds) {
    public record WorldBreakdown(int chunks, int entities, int hoppers, int redstone) {
    }
}
