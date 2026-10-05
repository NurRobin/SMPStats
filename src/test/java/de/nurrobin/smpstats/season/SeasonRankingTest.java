package de.nurrobin.smpstats.season;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SeasonRankingTest {
    private final UUID a = new UUID(0, 1), b = new UUID(0, 2), c = new UUID(0, 3), d = new UUID(0, 4), z = new UUID(0, 9);

    @Test
    void competitionRankingWithTies() {
        Map<UUID, Double> v = Map.of(a, 100.0, b, 50.0, c, 50.0, d, 10.0);
        assertEquals(Optional.of(new SeasonRanking.Rank(1, 4)), SeasonRanking.highest(v, a));
        assertEquals(Optional.of(new SeasonRanking.Rank(2, 4)), SeasonRanking.highest(v, b));
        assertEquals(Optional.of(new SeasonRanking.Rank(2, 4)), SeasonRanking.highest(v, c));
        assertEquals(Optional.of(new SeasonRanking.Rank(4, 4)), SeasonRanking.highest(v, d));
    }

    @Test
    void zeroAndMissingHaveNoRankAndDoNotCount() {
        Map<UUID, Double> v = Map.of(a, 5.0, b, 0.0);
        assertEquals(Optional.of(new SeasonRanking.Rank(1, 1)), SeasonRanking.highest(v, a));
        assertTrue(SeasonRanking.highest(v, b).isEmpty());
        assertTrue(SeasonRanking.highest(v, z).isEmpty());
    }

    @Test
    void earliestRanksByTime() {
        Map<UUID, Long> t = Map.of(a, 3_000L, b, 1_000L, c, 3_000L);
        assertEquals(Optional.of(new SeasonRanking.Rank(1, 3)), SeasonRanking.earliest(t, b));
        assertEquals(Optional.of(new SeasonRanking.Rank(2, 3)), SeasonRanking.earliest(t, a));
        assertTrue(SeasonRanking.earliest(t, z).isEmpty());
    }
}
