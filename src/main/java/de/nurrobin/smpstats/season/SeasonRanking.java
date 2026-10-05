package de.nurrobin.smpstats.season;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Competition ranking ("1, 2, 2, 4") over season values; only positive values take part. */
public final class SeasonRanking {

    public record Rank(int place, int total) {
    }

    private SeasonRanking() {
    }

    public static Optional<Rank> highest(Map<UUID, Double> values, UUID uuid) {
        Double own = values.get(uuid);
        if (own == null || own <= 0) {
            return Optional.empty();
        }
        int total = 0;
        int better = 0;
        for (double v : values.values()) {
            if (v > 0) {
                total++;
                if (v > own) {
                    better++;
                }
            }
        }
        return Optional.of(new Rank(better + 1, total));
    }

    public static Optional<Rank> earliest(Map<UUID, Long> times, UUID uuid) {
        Long own = times.get(uuid);
        if (own == null) {
            return Optional.empty();
        }
        int earlier = 0;
        for (long t : times.values()) {
            if (t < own) {
                earlier++;
            }
        }
        return Optional.of(new Rank(earlier + 1, times.size()));
    }
}
