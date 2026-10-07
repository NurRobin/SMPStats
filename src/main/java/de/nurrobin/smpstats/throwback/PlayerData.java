package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.season.SeasonPlayer;

import java.util.Map;

/** Everything the trophy value functions may read about one player of a season. */
public record PlayerData(SeasonPlayer player, Map<String, Double> stats, Map<String, Long> advancements) {

    public double stat(String key) {
        return stats.getOrDefault(key, 0.0);
    }

    public double sum(String... keys) {
        double total = 0;
        for (String key : keys) {
            total += stat(key);
        }
        return total;
    }

    public double sumPrefix(String prefix) {
        double total = 0;
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                total += e.getValue();
            }
        }
        return total;
    }

    /** The entry under {@code prefix} with the highest value, as (id, count); null if none is positive. */
    public Map.Entry<String, Double> top(String prefix) {
        Map.Entry<String, Double> best = null;
        for (Map.Entry<String, Double> e : stats.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue() > 0
                    && (best == null || e.getValue() > best.getValue()
                    || (e.getValue().equals(best.getValue()) && e.getKey().compareTo(best.getKey()) < 0))) {
                best = e;
            }
        }
        return best;
    }
}
