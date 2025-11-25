package de.nurrobin.smpstats.timeline;

import java.util.List;

public record DeathReplayEntry(
        long timestamp,
        String uuid,
        String name,
        String cause,
        double health,
        String world,
        int x,
        int y,
        int z,
        double fallDistance,
        List<String> nearbyPlayers,
        List<String> nearbyMobs,
        List<String> inventory
) {
}
