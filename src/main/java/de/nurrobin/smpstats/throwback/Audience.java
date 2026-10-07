package de.nurrobin.smpstats.throwback;

/** What a player gets to see in the hall. */
public enum Audience {
    /** In the 2025 season with at least the minimum playtime: trophies with their own values. */
    TROPHY,
    /** In the 2025 season but below the threshold: the tour for new players with a neutral welcome back. */
    RETURNING,
    /** Never seen on the 2025 server. */
    NEWCOMER
}
