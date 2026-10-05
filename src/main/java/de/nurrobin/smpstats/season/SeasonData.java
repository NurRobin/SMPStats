package de.nurrobin.smpstats.season;

import java.util.Map;
import java.util.UUID;

/** Everything read for one season, keyed by player. Stat values are raw (cm, ticks, counts). */
public record SeasonData(String season,
                         String source,
                         Map<UUID, SeasonPlayer> players,
                         Map<UUID, Map<String, Double>> stats,
                         Map<UUID, Map<String, Long>> advancements) {
}
