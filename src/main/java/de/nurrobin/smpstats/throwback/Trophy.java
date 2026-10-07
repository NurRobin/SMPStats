package de.nurrobin.smpstats.throwback;

import de.nurrobin.smpstats.season.SeasonRanking;

import java.util.Optional;

/** A trophy resolved for one player: the value plus the place among all players of the season. */
public record Trophy(TrophyDef def, Value value, Optional<SeasonRanking.Rank> rank, int poolIndex) {

    /** Place divided by the number of ranked players; smaller is better. Unranked counts as worst. */
    public double relative() {
        return rank.map(r -> (double) r.place() / r.total()).orElse(Double.MAX_VALUE);
    }

    public boolean showsBadge(int top) {
        return rank.isPresent() && rank.get().place() <= top;
    }
}
