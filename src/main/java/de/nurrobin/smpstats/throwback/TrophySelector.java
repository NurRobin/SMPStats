package de.nurrobin.smpstats.throwback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the trophies of one player: the "always" ones first, then the best by relative place. Deterministic:
 * equal relative places fall back to the pool order. Zero values never reach this class (the board drops them).
 */
public final class TrophySelector {
    private TrophySelector() {
    }

    public static List<Trophy> select(List<Trophy> candidates, int slots) {
        List<Trophy> fixed = new ArrayList<>();
        List<Trophy> rest = new ArrayList<>();
        for (Trophy t : candidates) {
            if (t.value().number() <= 0) {
                continue;
            }
            (t.def().always() ? fixed : rest).add(t);
        }
        fixed.sort(Comparator.comparingInt(Trophy::poolIndex));
        rest.sort(Comparator.comparingDouble(Trophy::relative).thenComparingInt(Trophy::poolIndex));
        List<Trophy> out = new ArrayList<>(fixed);
        out.addAll(rest);
        return out.size() > slots ? new ArrayList<>(out.subList(0, slots)) : out;
    }
}
