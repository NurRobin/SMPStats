package de.nurrobin.smpstats.throwback;

import java.util.function.Function;

/**
 * One category of the pool. {@code always} marks the three trophies every player gets (as long as the value is
 * above zero); the order of the catalog is the tie-break when relative ranks are equal.
 */
public record TrophyDef(String id, Kind kind, Unit unit, SceneType scene, boolean always,
                        Function<PlayerData, Value> value) {

    public enum Kind {
        /** A higher value is better. */
        HIGHEST,
        /** An earlier time is better (value is an epoch in ms). */
        EARLIEST
    }
}
